// Puente Nostr de Tansu web (se empaqueta en ../nostr.js con `npm run build` en web/lib).
// Firma y cifrado los hace un firmante externo: extensión del navegador (NIP-07) o bunker (NIP-46).
// Tansu no ve ni guarda ninguna nsec. Con bunker se guarda solo la clave de esa conexión.
import { BunkerSigner, createNostrConnectURI, parseBunkerInput } from "nostr-tools/nip46";
import { SimplePool } from "nostr-tools/pool";
import { generateSecretKey, getEventHash, getPublicKey, verifyEvent } from "nostr-tools/pure";
import * as nip19 from "nostr-tools/nip19";
import { bytesToHex, hexToBytes } from "nostr-tools/utils";

const pool = new SimplePool();
const bunkers = new Map();

export const verify = ev => { try { return verifyEvent(ev); } catch { return false; } };
export const npub = hex => nip19.npubEncode(hex);
/** npub, nprofile o hex → hex (o null). */
export function toHex(v) {
  v = String(v || "").trim();
  if (/^[0-9a-f]{64}$/i.test(v)) return v.toLowerCase();
  try { const d = nip19.decode(v.replace(/^nostr:/, "")); return d.type === "npub" ? d.data : d.type === "nprofile" ? d.data.pubkey : null; } catch { return null; }
}

export async function query(relays, filters, maxWait = 7000) {
  const out = new Map();
  await Promise.all(filters.map(f => pool.querySync(relays, f, { maxWait }).then(list => list.forEach(e => out.set(e.id, e))).catch(() => {})));
  return [...out.values()];
}
/** Relay a relay: [{ url, reached, events }], para saber en cuáles está cada evento. */
export async function queryEach(relays, filters, maxWait = 7000) {
  return Promise.all([...new Set(relays)].map(async url => {
    try {
      await pool.ensureRelay(url, { connectionTimeout: Math.min(maxWait, 5000) });
    } catch { return { url, reached: false, events: [] }; }
    const out = new Map();
    await Promise.all(filters.map(f => pool.querySync([url], f, { maxWait }).then(l => l.forEach(e => out.set(e.id, e))).catch(() => {})));
    return { url, reached: true, events: [...out.values()] };
  }));
}
/** Publica y devuelve cuántos relays lo aceptaron. */
export async function publish(relays, ev) {
  const r = await Promise.allSettled(pool.publish(relays, ev));
  return r.filter(x => x.status === "fulfilled").length;
}

// ---------- Bunker (NIP-46) ----------
export async function bunkerConnect(input, onauth) {
  const bp = await parseBunkerInput(String(input || "").trim());
  if (!bp || !bp.relays?.length) throw new Error("bunker");
  const sk = generateSecretKey();
  const s = BunkerSigner.fromBunker(sk, bp, { pool, onauth });
  await s.connect();
  const pubkey = await s.getPublicKey();
  bunkers.set(pubkey, s);
  return { pubkey, sk: bytesToHex(sk), bp };
}
/**
 * Conexión iniciada por Tansu (nostrconnect://, NIP-46): la abre Amber (u otro firmante) y responde por relays.
 * Así nada pasa por la dirección de la página ni por el almacenamiento del navegador.
 * onUri(uri) recibe el enlace para abrirlo; devuelve lo mismo que bunkerConnect.
 */
export async function nostrConnect(onUri, onauth, maxWait = 180000) {
  const sk = generateSecretKey();
  const secret = bytesToHex(generateSecretKey()).slice(0, 16);
  const uri = createNostrConnectURI({
    clientPubkey: getPublicKey(sk), relays: NC_RELAYS, secret, name: "Tansu",
    url: location.origin + location.pathname,
    perms: ["sign_event:30078", "sign_event:5", "nip44_encrypt", "nip44_decrypt", "nip04_decrypt", "get_public_key"],
  });
  onUri(uri);
  const s = await BunkerSigner.fromURI(sk, uri, { pool, onauth }, maxWait);
  const pubkey = await s.getPublicKey();
  bunkers.set(pubkey, s);
  return { pubkey, sk: bytesToHex(sk), bp: s.bp };
}
const NC_RELAYS = ["wss://relay.nsec.app", "wss://relay.damus.io", "wss://nos.lol"];
function bunker(pubkey, bp, skHex, onauth) {
  let s = bunkers.get(pubkey);
  if (!s) { s = BunkerSigner.fromBunker(hexToBytes(skHex), bp, { pool, onauth }); bunkers.set(pubkey, s); }
  return s;
}
export function forget(pubkey) { const s = bunkers.get(pubkey); bunkers.delete(pubkey); try { s?.close(); } catch {} }

// ---------- Firmante de una cuenta ----------
/**
 * acc = { pubkey, method: "nip07" | "bunker" | "read", bp?, sk? }
 * Devuelve { sign(template), enc44(peer, text), dec44(peer, text), dec04(peer, text) } o null (solo lectura).
 * Cada firma se comprueba: autor correcto y firma válida.
 */
export function signer(acc, onauth) {
  if (acc.method === "read") return null;
  const check = ev => { if (!ev || ev.pubkey !== acc.pubkey || !verify(ev)) throw new Error("firma"); return ev; };
  if (acc.method === "nip07") {
    const ext = () => {
      if (!window.nostr) throw new Error("nip07");
      return window.nostr;
    };
    const same = async () => { const pk = await ext().getPublicKey(); if (pk !== acc.pubkey) throw new Error("nip07-otra"); return ext(); };
    return {
      async sign(t) { const x = await same(); return check(await x.signEvent({ ...t, pubkey: acc.pubkey })); },
      async enc44(peer, text) { const x = await same(); if (!x.nip44) throw new Error("nip07-44"); return x.nip44.encrypt(peer, text); },
      async dec44(peer, text) { const x = await same(); if (!x.nip44) throw new Error("nip07-44"); return x.nip44.decrypt(peer, text); },
      async dec04(peer, text) { const x = await same(); if (!x.nip04) throw new Error("nip07-04"); return x.nip04.decrypt(peer, text); },
    };
  }
  const s = bunker(acc.pubkey, acc.bp, acc.sk, onauth);
  return {
    async sign(t) { return check(await s.signEvent({ kind: t.kind, created_at: t.created_at, tags: t.tags, content: t.content })); },
    enc44: (peer, text) => s.nip44Encrypt(peer, text),
    dec44: (peer, text) => s.nip44Decrypt(peer, text),
    dec04: (peer, text) => s.nip04Decrypt(peer, text),
  };
}
export async function nip07Pubkey() { if (!window.nostr) throw new Error("nip07"); return window.nostr.getPublicKey(); }
export const eventHash = getEventHash;
