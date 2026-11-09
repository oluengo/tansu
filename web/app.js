/* Tansu web: lo mismo que la app Android, en el navegador (PWA opcional).
   - Cuentas: Amber (como firmante remoto NIP-46 con nostrconnect://, en Android), extensión (NIP-07),
     bunker (NIP-46) o npub en solo lectura. Nunca se ve ni se guarda una nsec.
   - Todo lo que se publica va cifrado con NIP-44 para la propia cuenta (docs/decisiones/0001).
   - Lo descifrado solo vive en memoria: nunca se guarda en el navegador ni pasa por la dirección de la página.
     (Por eso Amber no usa NIP-55 por enlace en la web: devolvía el texto descifrado en la URL, que queda en el historial.) */
"use strict";
const N = window.TansuNostr, I18N = window.TANSU_I18N;
const $ = s => document.querySelector(s);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const LS = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
  del(k) { try { localStorage.removeItem(k); } catch {} },
};
const BETA = location.pathname.includes("/beta/");
const P = BETA ? "tansu-beta." : "tansu."; // los datos de la beta van aparte
const VERSION = "0.3.1" + (BETA ? " beta" : "");

// ---------- Idioma ----------
const LANGS = ["ca", "es", "en"];
let lang = "en";
function pickLang() {
  const s = LS.get(P + "lang", null);
  if (LANGS.includes(s)) return s;
  for (const l of navigator.languages || [navigator.language || "en"]) { const b = String(l).slice(0, 2).toLowerCase(); if (LANGS.includes(b)) return b; }
  return "en";
}
function T(k, ...a) {
  const s = I18N.s[lang][k] ?? I18N.s.en[k] ?? k;
  let i = 0;
  return s.replace(/%(\d+)\$[sd]|%[sd]/g, (m, n) => String(n ? a[n - 1] : a[i++]));
}
const TP = (k, n) => { const p = I18N.p[lang][k] || I18N.p.en[k]; return (n === 1 ? p[0] : p[1]).replace("%d", n); };

// ---------- Constantes ----------
const BUILTIN = ["wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net"];
const INDEXERS = ["wss://purplepag.es"];
const APP_DATA = 30078, DELETION = 5, RELAY_LIST = 10002;
const PART_SIZE = 40000;
const KNOWN = { wavestr: "Wavestr", usus: "Usus", mado: "Mado", kiroku: "Kiroku", bitacora: "Bitácora" };
const LIST_KINDS = {
  10000: "mute", 10001: "pins", 10003: "bookmarks", 10004: "communities", 10005: "chats", 10006: "blocked_relays",
  10007: "search_relays", 10009: "groups", 10015: "interests", 10030: "emojis", 10050: "dm_relays", 10063: "blossom",
  30000: "follow_set", 30002: "relay_set", 30003: "bookmark_set", 30004: "curation", 30005: "video_set", 30015: "interest_set", 30030: "emoji_set",
};
const META = new Set(["d", "title", "name", "description", "image", "summary", "alt"]);
const ACCENTS = {
  indigo: ["#3D4FA8", "#E9ECF8", "#9AA8F0", "#1F2440"], violet: ["#6D28D9", "#EDE6FB", "#A78BFA", "#2A2142"],
  green: ["#0E7C6B", "#DCEFEA", "#3FBFA6", "#1D332E"], coral: ["#D9480F", "#FDE9DF", "#FF8A5B", "#3A2318"],
  raspberry: ["#BE185D", "#FBE4EE", "#F472B6", "#3B1A2B"],
};
const AV_COLORS = ["#3D4FA8", "#B07A4A", "#7B4FD6", "#2E8A5A", "#C2410C", "#9D174D", "#334155"];
const REPO = "https://github.com/oluengo/tansu", NOSTRICH = "https://github.com/SovrynMatt/Nostr-Website-Button-Design";
const BTC = "bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa", LN = "oluengo@rizful.com";

// ---------- Estado ----------
const st = {
  accounts: LS.get(P + "accounts", []),   // [{pubkey, method: amber|nip07|bunker|read, label?, bp?, sk?}]
  active: LS.get(P + "active", null),
  settings: Object.assign({ theme: "system", accent: "indigo", relays: null }, LS.get(P + "settings", {})),
  profiles: LS.get(P + "profiles", {}),
  snaps: {}, loading: false, failed: false,
  opened: {}, listPriv: {},
  route: [{ v: "home" }], q: "",
  busy: null, draft: null, edit: null, install: null,
};
const acc = () => st.accounts.find(a => a.pubkey === st.active) || st.accounts[0] || null;
const appRelays = () => st.settings.relays || BUILTIN;
const saveAccounts = () => { LS.set(P + "accounts", st.accounts); LS.set(P + "active", st.active); };
const snap = () => acc() && st.snaps[acc().pubkey];
const okey = (pk, d) => pk + "|" + d;

// ---------- Utilidades ----------
const npub = hex => N.npub(hex);
const shortNpub = hex => { const n = npub(hex); return n.slice(0, 10) + "…" + n.slice(-6); };
const nameOf = a => a.label || st.profiles[a.pubkey]?.name || shortNpub(a.pubkey);
const avColor = pk => { let h = 0; for (const c of pk) h = (h * 31 + c.charCodeAt(0)) | 0; return AV_COLORS[Math.abs(h) % AV_COLORS.length]; };
const avatar = (a, cls = "") => {
  if (!a) return "";
  const p = st.profiles[a.pubkey], name = nameOf(a), pic = p?.picture ? `;background-image:url('${esc(encodeURI(p.picture))}')` : "";
  return `<span class="av ${cls}" style="background-color:${avColor(a.pubkey)}${pic}">${pic ? "" : esc(name[0]?.toUpperCase() || "?")}</span>`;
};
const methodLabel = m => T({ amber: "method_amber", nip07: "method_nip07", bunker: "method_bunker", read: "method_readonly" }[m]);
const canSign = a => !!a && a.method !== "read" && (a.method === "nip07" || !!(a.bp && a.sk));
const ago = sec => {
  const d = sec * 1000 - Date.now(), rtf = new Intl.RelativeTimeFormat(lang, { numeric: "auto" });
  const abs = Math.abs(d) / 1000;
  if (abs < 60) return rtf.format(Math.round(d / 1000), "second");
  if (abs < 3600) return rtf.format(Math.round(d / 60000), "minute");
  if (abs < 86400) return rtf.format(Math.round(d / 3600000), "hour");
  if (abs < 86400 * 45) return rtf.format(Math.round(d / 86400000), "day");
  return rtf.format(Math.round(d / (86400000 * 30)), "month");
};
const fullDate = sec => new Intl.DateTimeFormat(lang, { dateStyle: "medium", timeStyle: "short" }).format(new Date(sec * 1000));
const size = n => n < 1024 ? n + " B" : n < 1048576 ? (n / 1024).toFixed(n < 10240 ? 1 : 0) + " kB" : (n / 1048576).toFixed(1) + " MB";
const appName = app => app ? (KNOWN[app] || app) : T("folder_other");
const normRelay = input => {
  const t = String(input || "").trim().replace(/\/+$/, "").toLowerCase();
  if (!t || /\s/.test(t)) return null;
  const u = t.includes("://") ? t : "wss://" + t;
  const h = u.split("://")[1] || "";
  return (u.startsWith("wss://") || u.startsWith("ws://")) && (h.includes(".") || h.startsWith("localhost")) ? u : null;
};
const tag = (e, n) => e.tags.find(t => t[0] === n)?.[1];
const address = e => e.kind >= 30000 ? `${e.kind}:${e.pubkey}:${tag(e, "d") || ""}` : `${e.kind}:${e.pubkey}`;
const now = () => Math.floor(Date.now() / 1000);
let toastT = 0;
function toast(msg, ms = 2600) { const t = $("#toast"); t.textContent = msg; t.classList.add("on"); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove("on"), ms); }
const pretty = text => { try { return JSON.stringify(JSON.parse(text), null, 2); } catch { return text; } };
const isJson = text => { try { JSON.parse(text); return true; } catch { return false; } };
async function sha(s) { const b = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)); return [...new Uint8Array(b)].map(x => x.toString(16).padStart(2, "0")).join(""); }

// ---------- Contenido: cifrado, partes, gzip ----------
function encoding(content) {
  const t = String(content || "").trim();
  if (!t) return "empty";
  if (t.includes("?iv=")) return "nip04";
  if (t[0] === "{" || t[0] === "[") return "plain";
  try { const b = atob(t); return b.length >= 99 && b.charCodeAt(0) === 2 ? "nip44" : "plain"; } catch { return "plain"; }
}
async function gzB64(text) {
  const z = new Uint8Array(await new Response(new Blob([new TextEncoder().encode(text)]).stream().pipeThrough(new CompressionStream("gzip"))).arrayBuffer());
  let s = ""; for (let i = 0; i < z.length; i += 0x8000) s += String.fromCharCode(...z.subarray(i, i + 0x8000)); return btoa(s);
}
async function gunB64(b64) {
  const z = Uint8Array.from(atob(b64.trim()), c => c.charCodeAt(0));
  return new TextDecoder().decode(new Uint8Array(await new Response(new Blob([z]).stream().pipeThrough(new DecompressionStream("gzip"))).arrayBuffer()));
}
const chunks = (t, n = PART_SIZE) => { if (!t) return [""]; const o = []; for (let i = 0; i < t.length; i += n) o.push(t.slice(i, i + n)); return o; };
function latestByAddress(events) {
  const m = new Map();
  for (const e of events) { const a = address(e), o = m.get(a); if (!o || e.created_at > o.created_at || (e.created_at === o.created_at && e.id < o.id)) m.set(a, e); }
  return [...m.values()];
}
/** Agrupa los datos de apps (30078) en entradas; d/0…d/N se unen a su índice d (formato por partes de Usus). */
function group(events) {
  const byD = new Map();
  for (const e of latestByAddress(events.filter(e => e.kind === APP_DATA && tag(e, "d") != null))) byD.set(tag(e, "d"), e);
  const parts = new Map(), mains = [];
  for (const [d, e] of byD) {
    const m = d.match(/^(.+)\/(\d+)$/);
    if (m && byD.has(m[1])) { if (!parts.has(m[1])) parts.set(m[1], []); parts.get(m[1]).push([+m[2], e]); } else mains.push(e);
  }
  return mains.map(e => {
    const d = tag(e, "d"), app = d.includes("/") ? d.split("/")[0] : "";
    const ps = (parts.get(d) || []).sort((a, b) => a[0] - b[0]).map(x => x[1]);
    const all = [...ps, e];
    return { app, d, main: e, parts: ps, events: all, name: app ? (d.slice(app.length + 1) || d) : d, enc: encoding(e.content),
      updated: Math.max(...all.map(x => x.created_at)), bytes: all.reduce((s, x) => s + x.content.length, 0) };
  }).sort((a, b) => (a.app === "") - (b.app === "") || a.app.localeCompare(b.app) || b.updated - a.updated);
}
const listsOf = events => latestByAddress(events.filter(e => LIST_KINDS[e.kind])).sort((a, b) => (a.kind >= 30000) - (b.kind >= 30000) || a.kind - b.kind || b.created_at - a.created_at);
const publicItems = e => e.tags.filter(t => t.length > 1 && !META.has(t[0]));
const metaTags = e => e.tags.filter(t => t.length && (t.length < 2 || META.has(t[0])));
const listTitle = e => { const k = T("list_" + (LIST_KINDS[e.kind] || "other")), t = tag(e, "title") || tag(e, "name") || (e.kind >= 30000 ? tag(e, "d") : ""); return t ? `${t} · ${k}` : k; };
function parsePrivate(plain) { try { const a = JSON.parse(plain); return Array.isArray(a) ? a.filter(t => Array.isArray(t) && t.length > 1).map(t => t.map(String)) : null; } catch { return null; } }
function onlyRemovesPublic(orig, pub) { const before = publicItems(orig).map(t => JSON.stringify(t)); return pub.every(t => { const i = before.indexOf(JSON.stringify(t)); if (i < 0) return false; before.splice(i, 1); return true; }); }
const SUGGESTED = k => ({ 10000: ["p", "t", "word", "e"], 10001: ["e"], 10003: ["e", "a", "t", "r"], 30003: ["e", "a", "t", "r"], 10004: ["a"], 10005: ["e"],
  10006: ["relay"], 10007: ["relay"], 10050: ["relay"], 30002: ["relay"], 10009: ["group", "r"], 10015: ["t", "a"], 30015: ["t", "a"], 10030: ["emoji", "a"],
  30030: ["emoji", "a"], 10063: ["server"], 30000: ["p"], 30004: ["a", "e"], 30005: ["e"] }[k] || ["p", "e", "a", "t", "r"]);

// ---------- Errores ----------
class TErr extends Error { constructor(code) { super(code); this.code = code; } }
function errText(e) {
  const c = e?.code || e?.message || "";
  if (c === "nip07") return T("nip07_missing");
  if (c === "nip07-otra") return T("nip07_other");
  if (c === "nip07-44") return T("nip07_no44");
  return T({ signer: "err_signer", read: "err_readonly", offline: "err_offline", unreadable: "err_unreadable", big: "err_too_big",
    notenc: "err_not_encrypted", public: "err_would_publish" }[c] || (/reject|denied|cancel|firma/i.test(c) ? "err_signer" : "err_offline"));
}

// Restos de la versión anterior (Amber por enlace NIP-55): se borran siempre al abrir.
const LEGACY = [P + "amber.res", P + "amber.req", P + "op", P + "route"];
const isAndroid = /Android/i.test(navigator.userAgent);
const onAuth = url => { try { window.open(url, "_blank", "noopener"); } catch {} toast(T("bunker_auth"), 6000); };
function signerOf(a) {
  if (!canSign(a)) throw new TErr("read");
  // Amber en la web va como bunker (nostrconnect), con su conexión guardada (bp/sk)
  return N.signer(a.method === "amber" ? { ...a, method: "bunker" } : a, onAuth);
}

// ---------- Relays y lectura ----------
async function relaysFor(pk) {
  const ev = (await N.query([...INDEXERS, ...appRelays()], [{ kinds: [RELAY_LIST], authors: [pk], limit: 1 }], 5000))
    .filter(e => e.pubkey === pk).sort((a, b) => b.created_at - a.created_at)[0];
  const own = (ev?.tags || []).filter(t => t[0] === "r" && t[1]).map(t => normRelay(t[1])).filter(Boolean).slice(0, 6);
  return [...new Set([...own, ...appRelays()])];
}
async function load(pk) {
  const relays = await relaysFor(pk);
  const evs = (await N.query(relays, [
    { kinds: [APP_DATA], authors: [pk], limit: 500 },
    { kinds: Object.keys(LIST_KINDS).map(Number), authors: [pk], limit: 200 },
  ], 8000)).filter(e => e.pubkey === pk && N.verify(e));
  return { relays, entries: group(evs), lists: listsOf(evs), at: Date.now() };
}
async function refresh() {
  const a = acc(); if (!a) return;
  st.loading = true; st.failed = false; render();
  try { st.snaps[a.pubkey] = await load(a.pubkey); } catch { if (!st.snaps[a.pubkey]) st.failed = true; }
  st.loading = false; render();
}
async function loadProfiles() {
  const miss = st.accounts.map(a => a.pubkey).filter(pk => !st.profiles[pk]);
  if (!miss.length) return;
  try {
    const evs = await N.query([...INDEXERS, ...appRelays()], [{ kinds: [0], authors: miss }], 5000);
    for (const pk of miss) {
      const e = evs.filter(x => x.pubkey === pk).sort((a, b) => b.created_at - a.created_at)[0];
      let o = {}; try { o = JSON.parse(e?.content || "{}"); } catch {}
      st.profiles[pk] = { name: (o.display_name || o.name || "").trim() || null, picture: /^https:\/\//.test(o.picture || "") ? o.picture : null };
    }
    LS.set(P + "profiles", st.profiles); render();
  } catch {}
}

// ---------- Escribir (siempre cifrado) ----------
async function publishSigned(s, relays, t) {
  const ev = await s.sign(t);
  if (await N.publish(relays, ev) === 0) throw new TErr("offline");
  return ev;
}
async function decode(s, pk, content) {
  const enc = encoding(content);
  if (enc === "nip44") return await s.dec44(pk, content);
  if (enc === "nip04") return await s.dec04(pk, content);
  return content;
}
async function openEntry(a, e) {
  const k = okey(a.pubkey, e.d);
  if (st.opened[k]) return st.opened[k];
  const s = e.enc === "plain" || e.enc === "empty" ? null : signerOf(a);
  const first = s ? await decode(s, a.pubkey, e.main.content) : e.main.content;
  let o;
  if (!e.parts.length) o = { text: first, manifest: null };
  else {
    let m; try { m = JSON.parse(first); } catch { throw new TErr("unreadable"); }
    const n = Number.isInteger(m.parts) ? m.parts : e.parts.length;
    if (e.parts.length < n) throw new TErr("unreadable");
    let z = ""; for (const p of e.parts.slice(0, n)) z += await decode(signerOf(a), a.pubkey, p.content);
    try { o = { text: await gunB64(z), manifest: m }; } catch { throw new TErr("unreadable"); }
  }
  st.opened[k] = o;
  return o;
}
async function writeEntry(a, relays, d, text, manifest, oldParts, at) {
  const s = signerOf(a), me = a.pubkey;
  let writes = [[d, text]], extra = [];
  if (manifest) {
    const z = await gzB64(text), ps = chunks(z);
    const m = { ...manifest, parts: ps.length, len: z.length, t: at * 1000 };
    writes = [...ps.map((p, i) => [`${d}/${i}`, p]), [d, JSON.stringify(m)]];
    for (let i = ps.length; i < oldParts; i++) extra.push(`${d}/${i}`);
  } else for (let i = 0; i < oldParts; i++) extra.push(`${d}/${i}`);
  let i = 0;
  for (const [dd, plain] of writes) {
    st.busy = T("busy_step", ++i, writes.length); render();
    if (new TextEncoder().encode(plain).length > 65000) throw new TErr("big");
    const enc = await s.enc44(me, plain);
    if (encoding(enc) !== "nip44") throw new TErr("notenc");
    await publishSigned(s, relays, { kind: APP_DATA, created_at: at, tags: [["d", dd]], content: enc });
  }
  if (extra.length) try { await publishSigned(s, relays, { kind: DELETION, created_at: at, tags: [...extra.map(x => ["a", `${APP_DATA}:${me}:${x}`]), ["k", String(APP_DATA)]], content: "" }); } catch {}
}

// ---------- Papelera (IndexedDB; solo en este navegador) ----------
const DB = {
  db: null,
  open() {
    return this.db ||= new Promise((ok, ko) => {
      const r = indexedDB.open(P + "db", 1);
      r.onupgradeneeded = () => r.result.createObjectStore("trash", { keyPath: "id" });
      r.onsuccess = () => ok(r.result); r.onerror = () => ko(r.error);
    });
  },
  async tx(mode, f) { const db = await this.open(); return new Promise((ok, ko) => { const t = db.transaction("trash", mode), s = t.objectStore("trash"), r = f(s); t.oncomplete = () => ok(r?.result); t.onerror = () => ko(t.error); }); },
  all() { return this.tx("readonly", s => s.getAll()).then(l => (l || []).sort((a, b) => b.at - a.at)); },
  put(item) { return this.tx("readwrite", s => s.put(item)); },
  del(id) { return this.tx("readwrite", s => s.delete(id)); },
  clear() { return this.tx("readwrite", s => s.clear()); },
};
let trashCache = [];
const loadTrash = async () => { try { trashCache = await DB.all(); } catch { trashCache = []; } };
async function toTrash(pubkey, label, events, id) {
  // id fijo por operación: al repetirla tras volver de Amber no se duplica
  await DB.put({ id, pubkey, d: label, at: Date.now(), events }); await loadTrash();
}

// ---------- Operaciones (se pueden retomar al volver de Amber) ----------
const OPS = {
  async open({ d }) {
    const a = acc(), e = snap()?.entries.find(x => x.d === d); if (!e) throw new TErr("unreadable");
    await openEntry(a, e);
  },
  async openList({ addr }) {
    const a = acc(), e = snap()?.lists.find(x => address(x) === addr); if (!e) throw new TErr("unreadable");
    const items = parsePrivate(await decode(signerOf(a), a.pubkey, e.content));
    if (!items) throw new TErr("unreadable");
    st.listPriv[addr] = items;
  },
  async save({ d, text, manifest, at, tid }) {
    const a = acc(), sn = snap(), e = sn?.entries.find(x => x.d === d); if (!e) throw new TErr("unreadable");
    await toTrash(a.pubkey, d, e.events, tid);
    await writeEntry(a, sn.relays, d, text, manifest, e.parts.length, at);
    st.opened[okey(a.pubkey, d)] = { text, manifest };
    toast(T("saved")); st.route.pop(); refresh();
  },
  async saveList({ addr, pub, priv, at, tid }) {
    const a = acc(), sn = snap(), e = sn?.lists.find(x => address(x) === addr); if (!e) throw new TErr("unreadable");
    if (!onlyRemovesPublic(e, pub)) throw new TErr("public");
    await toTrash(a.pubkey, tag(e, "title") || tag(e, "d") || "kind " + e.kind, [e], tid);
    const s = signerOf(a);
    let content = "";
    if (priv.length) {
      const plain = JSON.stringify(priv);
      if (new TextEncoder().encode(plain).length > 65000) throw new TErr("big");
      content = await s.enc44(a.pubkey, plain);
      if (encoding(content) !== "nip44") throw new TErr("notenc");
    }
    const saved = await publishSigned(s, sn.relays, { kind: e.kind, created_at: Math.max(at, e.created_at + 1), tags: [...metaTags(e), ...pub], content });
    sn.lists = sn.lists.map(x => address(x) === addr ? saved : x);
    st.listPriv[addr] = priv; st.draft = null;
    toast(T("saved"));
  },
  async copy({ d, target, at }) {
    const a = acc(), e = snap()?.entries.find(x => x.d === d); if (!e) throw new TErr("unreadable");
    const t = st.accounts.find(x => x.pubkey === target); if (!canSign(t)) throw new TErr("read");
    const o = await openEntry(a, e);
    const relays = await relaysFor(target);
    const old = st.snaps[target]?.entries.find(x => x.d === d);
    await writeEntry(t, relays, d, o.text, o.manifest, old ? old.parts.length : e.parts.length, at);
    delete st.snaps[target];
    toast(T("copied_to_account"));
  },
  async del({ d, at, tid }) {
    const a = acc(), sn = snap(), e = sn?.entries.find(x => x.d === d); if (!e) throw new TErr("unreadable");
    await toTrash(a.pubkey, d, e.events, tid);
    const tags = [...e.events.flatMap(x => [["e", x.id], ["a", address(x)]]), ["k", String(APP_DATA)]];
    await publishSigned(signerOf(a), sn.relays, { kind: DELETION, created_at: at, tags, content: "" });
    sn.entries = sn.entries.filter(x => x.d !== d); delete st.opened[okey(a.pubkey, d)];
    toast(T("deleted")); st.route.pop();
  },
  async restore({ id, at }) {
    const item = trashCache.find(x => x.id === id); if (!item) return;
    const a = st.accounts.find(x => x.pubkey === item.pubkey); if (!canSign(a)) throw new TErr("read");
    const s = signerOf(a), relays = await relaysFor(a.pubkey);
    let i = 0;
    for (const e of item.events) {
      st.busy = T("busy_step", ++i, item.events.length); render();
      if (encoding(e.content) === "plain") throw new TErr("notenc");
      await publishSigned(s, relays, { kind: e.kind, created_at: at, tags: e.tags, content: e.content });
    }
    await DB.del(id); await loadTrash(); delete st.snaps[a.pubkey];
    toast(T("restored"));
    if (a.pubkey === acc()?.pubkey) refresh();
  },
};
async function run(name, args = {}) {
  st.busy = T("busy_signer"); render();
  try {
    await OPS[name]({ at: now(), tid: Math.random().toString(36).slice(2), ...args });
    return true;
  } catch (e) {
    toast(errText(e), 4500);
    return false;
  } finally {
    st.busy = null; render();
  }
}

// ---------- Cuentas ----------
function addAccount(a) {
  const old = st.accounts.find(x => x.pubkey === a.pubkey);
  st.accounts = [...st.accounts.filter(x => x.pubkey !== a.pubkey), { ...a, label: a.label ?? old?.label }];
  st.active = a.pubkey; saveAccounts(); st.route = [{ v: "home" }]; st.sheet = null;
  closeLayer(); loadProfiles(); refresh();
}
function setActive(pk) { if (pk === st.active) return; st.active = pk; saveAccounts(); st.route = [{ v: "home" }]; st.q = ""; render(); if (!st.snaps[pk]) refresh(); else refresh(); }
function removeAccount(pk) {
  N.forget(pk); st.accounts = st.accounts.filter(a => a.pubkey !== pk);
  delete st.snaps[pk]; Object.keys(st.opened).forEach(k => k.startsWith(pk + "|") && delete st.opened[k]);
  if (st.active === pk) st.active = st.accounts[0]?.pubkey || null;
  saveAccounts(); st.route = st.accounts.length ? st.route : [{ v: "home" }]; render(); if (st.active) refresh();
}
/** Amber en la web: Tansu crea un enlace nostrconnect:// que abre Amber; Amber responde por relays (NIP-46). */
let ncAbort = null;
async function loginAmber() {
  let uri = "";
  dialog(`<h3>${T("login_amber")}</h3><div class="busy"><div class="spin"></div><div>${T("nc_waiting")}</div></div>
    <div class="acts"><button class="btn" data-a="nccancel">${T("cancel")}</button><a id="nc-open" class="btn p" href="#">${T("nc_open")}</a></div>`);
  const ctl = ncAbort = new AbortController();
  try {
    const r = await N.nostrConnect(u => { uri = u; const l = $("#nc-open"); if (l) l.href = u; try { location.href = u; } catch {} }, onAuth, ctl.signal);
    if (ctl.signal.aborted) return;
    addAccount({ pubkey: r.pubkey, method: "amber", bp: r.bp, sk: r.sk });
  } catch {
    if (!ctl.signal.aborted) { closeLayer(); toast(T("bunker_failed"), 4500); }
  }
}
async function loginNip07() {
  if (!window.nostr) return toast(T("nip07_missing"), 4500);
  try { const pk = await N.nip07Pubkey(); if (!/^[0-9a-f]{64}$/.test(pk)) throw 0; addAccount({ pubkey: pk, method: "nip07" }); }
  catch { toast(T("err_signer")); }
}
async function loginBunker() {
  const input = $("#bunker-in")?.value.trim(); if (!input) return;
  const err = $("#bunker-err"), btn = $("#bunker-go");
  btn.disabled = true; err.textContent = T("bunker_connecting");
  try {
    const r = await Promise.race([N.bunkerConnect(input, onAuth), new Promise((_, ko) => setTimeout(() => ko(new Error("timeout")), 120000))]);
    addAccount({ pubkey: r.pubkey, method: "bunker", bp: r.bp, sk: r.sk });
  } catch (e) {
    err.textContent = e?.message === "bunker" ? T("bunker_bad_uri") : T("bunker_failed"); btn.disabled = false;
  }
}
function loginNpub() {
  const pk = N.toHex($("#npub-in")?.value);
  if (!pk) { $("#npub-err").textContent = T("npub_invalid"); return; }
  addAccount({ pubkey: pk, method: "read" });
}

// ---------- Tema ----------
function applyTheme() {
  const s = st.settings, dark = s.theme === "dark" || (s.theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  const r = document.documentElement, c = ACCENTS[s.accent] || ACCENTS.indigo;
  r.dataset.theme = dark ? "dark" : "light";
  r.style.setProperty("--accent", dark ? c[2] : c[0]);
  r.style.setProperty("--accent-soft", dark ? c[3] : c[1]);
  r.style.setProperty("--on-accent", dark ? "#0E0F14" : "#fff");
  document.querySelector('meta[name=theme-color]').content = dark ? "#121318" : "#F7F7F4";
  r.lang = lang;
}
const saveSettings = () => { LS.set(P + "settings", st.settings); applyTheme(); render(); };


// ---------- Vista «Visual» del JSON (mismas reglas que JsonView.kt en Android) ----------
// Genérica: fichas clave → valor, árboles de carpetas, tablas, etiquetas, fechas legibles y sí/no. Solo para leer.
const JV = {
  isObj: v => v && typeof v === "object" && !Array.isArray(v),
  isPrim: v => v === null || typeof v !== "object",
  human: k => { const s = String(k).replace(/_/g, " ").replace(/([a-z])([A-Z])/g, "$1 $2").toLowerCase(); return s.charAt(0).toUpperCase() + s.slice(1); },
  DATE_KEY: /(^|_)(t|ts|at|date|time|created|updated|exported|last|since|until)($|_|[A-Z])|At$|Date$|Time$|^last|^created|^updated/i,
  date(v, key) {
    if (typeof v === "string" && /^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?)?$/.test(v)) { const d = new Date(v.length === 10 ? v + "T00:00:00Z" : v); return isNaN(d) ? null : d; }
    if (typeof v === "number" && JV.DATE_KEY.test(String(key || ""))) {
      if (v >= 1e12 && v < 1e13) return new Date(v);
      if (v >= 1e9 && v < 1e10) return new Date(v * 1000);
    }
    return null;
  },
  title: o => o.name ?? o.title ?? o.label ?? o.d ?? null,
  kids: o => ["children", "items", "folders", "entries", "files"].find(k => Array.isArray(o[k])),
  isTree: a => a.length > 0 && a.every(o => JV.isObj(o) && JV.title(o) != null) && a.some(o => JV.kids(o)),
  isChips: a => a.length <= 40 && a.every(v => typeof v === "string" && v.length <= 40),
  isTable: a => a.length > 0 && a.every(o => JV.isObj(o) && Object.values(o).every(v => JV.isPrim(v) || (Array.isArray(v) && v.every(JV.isPrim)))),
  isFlat: v => JV.isPrim(v) || (Array.isArray(v) && v.length <= 12 && JV.isChips(v)),
};
let jvId = 0;
function jvPrim(v, key, cell) {
  if (v === null || v === undefined) return `<span class="muted">—</span>`;
  if (typeof v === "boolean") return cell ? `<b class="${v ? "ok" : "danger"}">${v ? "✓" : "✗"}</b>` : `<span class="jchip ${v ? "yes" : "no"}">${v ? "✓ " + T("value_yes") : "✗ " + T("value_no")}</span>`;
  const d = JV.date(v, key);
  if (d) return cell ? esc(new Intl.DateTimeFormat(lang, { dateStyle: "medium" }).format(d))
    : `<span>${esc(new Intl.DateTimeFormat(lang, { dateStyle: "medium", timeStyle: typeof v === "string" && v.length === 10 ? undefined : "short" }).format(d))}<small class="mono muted" style="display:block;font-size:11px">${esc(v)}</small></span>`;
  if (typeof v === "number") return `<span class="mono">${esc(v.toLocaleString(lang))}</span>`;
  if (v === "") return `<span class="muted">${T("value_empty_text")}</span>`;
  if (/^https?:\/\/\S+$/.test(v)) return `<a href="${esc(v)}" target="_blank" rel="noopener noreferrer">${esc(v)}</a>`;
  if (/^wss?:\/\/\S+$/.test(v)) return `<span class="mono">${esc(v)}</span>`;
  return esc(v);
}
const jvChips = a => a.length ? a.map(v => `<span class="jchip">${esc(v)}</span>`).join("") : `<span class="muted small">${T("value_empty_list")}</span>`;
function jvSection(title, v, open) {
  const cnt = Array.isArray(v) ? v.length : JV.isObj(v) ? TP("fields", Object.keys(v).length) : "";
  return `<details class="jsec" ${open ? "open" : ""}><summary><span class="jchev">›</span>${esc(title)}<span class="jcnt">${esc(cnt)}</span></summary><div class="jbody">${jvNode(v)}</div></details>`;
}
function jvTree(a) {
  return `<ul class="jtree">${a.map(o => {
    const ck = JV.kids(o), kids = ck ? o[ck] : null, t = JV.title(o);
    const meta = Object.entries(o).filter(([k, v]) => k !== ck && JV.isPrim(v) && v !== null && v !== t).map(([k, v]) => JV.human(k) + ": " + v).join(" · ");
    const head = `<div class="jnode"><span class="jic ${kids ? "f" : ""}">${kids ? "📁" : "📄"}</span><span class="t"><div class="n">${esc(t ?? "")}</div>${meta ? `<div class="m">${esc(meta)}</div>` : ""}</span>${kids ? `<span class="jcnt">${kids.length}</span>` : ""}</div>`;
    if (!kids) return `<li>${head}</li>`;
    const inner = !kids.length ? `<div class="muted small" style="padding-left:36px">${T("value_empty_list")}</div>`
      : kids.every(k => JV.isObj(k) && JV.title(k) != null) ? jvTree(kids) : `<div style="padding-left:36px">${jvNode(kids)}</div>`;
    return `<li>${head}${inner}</li>`;
  }).join("")}</ul>`;
}
function jvTable(a) {
  const cols = []; for (const o of a) for (const k of Object.keys(o)) if (!cols.includes(k)) cols.push(k);
  const id = "jt" + (++jvId), LIM = 15;
  const cell = (o, k) => { const v = o[k]; if (v === undefined) return ""; return Array.isArray(v) ? esc(v.join(", ")) : jvPrim(v, k, true); };
  return `<div class="jtw"><table class="jtab"><thead><tr>${cols.map(k => `<th title="${esc(k)}">${esc(JV.human(k))}</th>`).join("")}</tr></thead><tbody>${a.map((o, i) => `<tr ${i >= LIM ? `class="${id}" hidden` : ""}>${cols.map(k => `<td>${cell(o, k)}</td>`).join("")}</tr>`).join("")}</tbody></table>${a.length > LIM ? `<button class="jmore" data-a="jvrows" data-x="${id}">${T("view_rows", a.length)}</button>` : ""}</div>`;
}
function jvNode(v) {
  if (JV.isPrim(v)) return jvPrim(v);
  if (Array.isArray(v)) {
    if (!v.length) return `<span class="muted small">${T("value_empty_list")}</span>`;
    if (JV.isChips(v)) return jvChips(v);
    if (v.every(JV.isPrim)) return `<ol class="jol">${v.map(x => `<li>${jvPrim(x)}</li>`).join("")}</ol>`;
    if (JV.isTree(v)) return jvTree(v);
    if (JV.isTable(v)) return jvTable(v);
    return v.map((x, i) => jvSection((JV.isObj(x) && JV.title(x)) || `#${i + 1}`, x, false)).join("");
  }
  const ent = Object.entries(v);
  if (!ent.length) return `<span class="muted small">${T("value_empty_text")}</span>`;
  const flat = ent.filter(([, x]) => JV.isFlat(x)), nested = ent.filter(([, x]) => !JV.isFlat(x));
  let h = flat.length ? `<dl class="jkv">${flat.map(([k, x]) => `<div><dt title="${esc(k)}">${esc(JV.human(k))}</dt><dd>${Array.isArray(x) ? jvChips(x) : jvPrim(x, k)}</dd></div>`).join("")}</dl>` : "";
  for (const [k, x] of nested) h += jvSection(JV.human(k), x, nested.length <= 3);
  return h;
}

// ---------- Vistas ----------
const LOGO = `<img class="logo" src="icon.svg" alt="">`;
const cur = () => st.route[st.route.length - 1];
function go(r) { st.route.push(r); history.pushState({ n: st.route.length }, ""); render(); scrollTo(0, 0); }
function back() { if (st.route.length > 1) history.back(); }
addEventListener("popstate", () => { if (st.route.length > 1) { st.route.pop(); st.draft = null; st.edit = null; render(); } });

function render() {
  applyTheme();
  const app = $("#app");
  if (!st.accounts.length) { app.innerHTML = welcome(); renderBusy(); return; }
  const r = cur();
  let h = topBar(r);
  const sn = snap();
  if (r.v === "settings") h += settings(r.sec);
  else if (!sn && st.failed) h += `<div class="center"><p class="muted">${T("load_failed")}</p><button class="btn" data-a="refresh">${T("retry")}</button></div>`;
  else if (!sn) h += `<div class="center"><div class="spin" style="margin:30px auto 12px"></div><p class="muted">${T("loading")}</p></div>`;
  else h += ({ home, folder, lists, item, edit, list }[r.v] || home)(sn, r);
  const focus = document.activeElement?.id, pos = document.activeElement?.selectionStart;
  app.innerHTML = h;
  if (focus) { const el = document.getElementById(focus); if (el) { el.focus(); try { el.setSelectionRange(pos, pos); } catch {} } }
  renderBusy();
}
function renderBusy() {
  let b = document.getElementById("busy");
  if (!st.busy) { b?.remove(); return; }
  if (!b) { b = document.createElement("div"); b.id = "busy"; b.className = "shade mid"; document.body.appendChild(b); }
  b.innerHTML = `<div class="dialog"><div class="busy"><div class="spin"></div><div>${esc(st.busy)}</div></div></div>`;
}
function topBar(r) {
  const a = acc();
  const left = r.v === "home"
    ? `${LOGO}<h1>${BETA ? "Tansu Beta" : "Tansu"}</h1>`
    : `<button class="icon-btn" data-a="back" aria-label="${T("back")}">←</button><h1>${esc(title(r))}</h1>`;
  const chip = `<button class="chip" data-a="accounts">${avatar(a)}${r.v === "home" ? `<span>${esc(nameOf(a))}</span>` : ""}</button>`;
  const gear = r.v === "home" ? `<button class="icon-btn" data-a="go" data-x='{"v":"settings"}' aria-label="${T("settings")}">⚙</button>` : "";
  return `<div class="top">${left}${r.v === "settings" ? "" : chip}${gear}</div>`;
}
function title(r) {
  const sn = snap();
  if (r.v === "folder") return appName(r.app);
  if (r.v === "lists") return T("folder_lists");
  if (r.v === "item") return sn?.entries.find(e => e.d === r.d)?.name || r.d;
  if (r.v === "edit") return T("edit");
  if (r.v === "list") { const e = sn?.lists.find(x => address(x) === r.addr); return e ? listTitle(e) : ""; }
  if (r.v === "settings") return r.sec ? T({ accounts: "accounts", appearance: "settings_appearance", relays: "settings_relays", trash: "trash", about: "settings_about" }[r.sec]) : T("settings");
  return "Tansu";
}
const J = o => esc(JSON.stringify(o));
function encBadge(enc) {
  if (enc === "nip44") return `<span class="badge lock">NIP-44</span>`;
  if (enc === "nip04") return `<span class="badge lock">NIP-04</span>`;
  if (enc === "plain") return `<span class="badge plain">${T("badge_plain")}</span>`;
  return `<span class="badge">${T("badge_empty")}</span>`;
}
const folderRow = (name, meta, r, icon = "📁") =>
  `<button class="row" data-a="go" data-x='${J(r)}'><span class="ico f">${icon}</span><span class="t"><div class="n">${esc(name)}</div><div class="m">${esc(meta)}</div></span><span class="chev">›</span></button>`;
const entryRow = (e, full) =>
  `<button class="row" data-a="go" data-x='${J({ v: "item", d: e.d })}'><span class="ico">${e.enc === "nip44" || e.enc === "nip04" ? "🔒" : "📄"}</span><span class="t"><div class="n">${esc(full ? e.d : e.name)}</div><div class="m">${esc(ago(e.updated) + " · " + size(e.bytes) + (e.parts.length ? " · " + TP("parts", e.parts.length) : ""))}</div></span>${encBadge(e.enc)}</button>`;
const listRow = e =>
  `<button class="row" data-a="go" data-x='${J({ v: "list", addr: address(e) })}'><span class="ico">☰</span><span class="t"><div class="n">${esc(listTitle(e))}</div><div class="m">${esc(`kind ${e.kind} · ${TP("public_items", publicItems(e).length)} · ${ago(e.created_at)}`)}</div></span>${e.content ? encBadge(encoding(e.content)) : ""}</button>`;

function home(sn) {
  let h = `<label class="search">🔍<input id="q" data-in="q" placeholder="${T("search_hint")}" value="${esc(st.q)}" autocomplete="off">${st.q ? `<button class="btn t" data-a="clearq">✕</button>` : ""}</label>`;
  if (st.loading) h += `<div class="small muted" style="padding:4px 10px">${T("loading")}</div>`;
  const q = st.q.trim().toLowerCase();
  if (q) {
    const es = sn.entries.filter(e => e.d.toLowerCase().includes(q)), ls = sn.lists.filter(e => (listTitle(e) + " " + e.kind).toLowerCase().includes(q));
    h += `<div class="sec">${TP("results", es.length + ls.length)}</div>` + es.map(e => entryRow(e, true)).join("") + ls.map(listRow).join("");
    if (!es.length && !ls.length) h += `<p class="muted" style="padding:10px">${T("search_none")}</p>`;
    return h;
  }
  const groups = {};
  for (const e of sn.entries) (groups[e.app] ||= []).push(e);
  h += `<div class="sec">${T("drawer_apps")}</div>`;
  if (!sn.entries.length) h += `<p class="muted" style="padding:0 10px">${T("apps_empty")}</p>`;
  for (const [app, l] of Object.entries(groups)) h += folderRow(appName(app), TP("items", l.length) + " · " + ago(Math.max(...l.map(x => x.updated))), { v: "folder", app });
  h += `<div class="sec">${T("drawer_lists")}</div>` + folderRow(T("folder_lists"), TP("items", sn.lists.length), { v: "lists" }, "☰");
  h += `<p class="small muted" style="padding:14px 10px">${T("relays_used", sn.relays.length)} · ${T("privacy_short")} · <button class="btn t small" data-a="refresh">↻ ${T("retry")}</button></p>`;
  return h;
}
const folder = (sn, r) => { const l = sn.entries.filter(e => e.app === r.app); return l.length ? l.map(e => entryRow(e)).join("") : `<p class="muted">${T("folder_empty")}</p>`; };
const lists = sn => `<p class="small muted" style="padding:0 10px">${T("lists_note")}</p>` + (sn.lists.length ? sn.lists.map(listRow).join("") : `<p class="muted">${T("lists_empty")}</p>`);
const PREVIEW = 20000;
function item(sn, r) {
  const a = acc(), e = sn.entries.find(x => x.d === r.d);
  if (!e) return `<p class="muted">${T("item_gone")}</p>`;
  const o = st.opened[okey(a.pubkey, e.d)];
  const plain = (e.enc === "plain" || e.enc === "empty") && !e.parts.length ? e.main.content : null;
  const text = o?.text ?? plain;
  let h = `<div class="badges">${encBadge(e.enc)}<span class="badge">${esc(appName(e.app))}</span>${e.parts.length ? `<span class="badge">${TP("parts", e.parts.length)}</span>` : ""}<span class="badge">${size(e.bytes)}</span></div>`;
  h += `<div class="group"><dl class="info"><dt>d</dt><dd class="mono">${esc(e.d)}</dd><dt>kind</dt><dd>30078 · NIP-78</dd><dt>${T("info_updated")}</dt><dd>${fullDate(e.updated)}</dd>`;
  if (o?.manifest) for (const k of ["app", "profile", "v"]) if (o.manifest[k] != null && typeof o.manifest[k] !== "object") h += `<dt>${k}</dt><dd>${esc(o.manifest[k])}</dd>`;
  h += `</dl></div>`;
  if (e.enc === "plain") h += `<p class="small danger">${T("plain_warning")}</p>`;
  let json; try { json = text != null ? JSON.parse(text) : undefined; } catch { json = undefined; }
  const visual = json !== undefined && LS.get(P + "view", "visual") === "visual";
  const seg = json === undefined ? "" : `<div class="seg mini">${["visual", "code"].map(m => `<button class="${(m === "visual") === visual ? "on" : ""}" data-a="view" data-x="${m}">${T(m === "visual" ? "view_visual" : "view_code")}</button>`).join("")}</div>`;
  h += `<div class="group np"><div class="hd">${T("content")}<span class="sp"></span>${seg || (text != null ? `<span class="small muted">${size(new TextEncoder().encode(text).length)}</span>` : "")}</div>`;
  if (visual) h += `<div class="jv">${jvNode(json)}</div>`;
  else if (text != null) {
    const p = pretty(text), cut = !r.all && p.length > PREVIEW;
    h += `<pre class="content mono">${esc(cut ? p.slice(0, PREVIEW) + "\n…" : p)}</pre>`;
    if (cut) h += `<div style="padding:0 8px 8px"><button class="btn t" data-a="showall">${T("show_all")}</button></div>`;
  } else if (!canSign(a)) h += `<p class="muted center">${T("readonly_cannot_decrypt")}</p>`;
  else h += `<div class="center"><p class="small muted">${T("encrypted_for_you")}</p><button class="btn p" data-a="open" data-x="${esc(e.d)}">${T("decrypt")}</button></div>`;
  h += `</div><div class="acts">`;
  if (o && e.enc === "nip44" && canSign(a)) h += `<button class="btn" data-a="go" data-x='${J({ v: "edit", d: e.d })}'>${T("edit")}</button>`;
  if (text != null) h += `<button class="btn" data-a="export" data-x="${esc(e.d)}">${T("export")}</button>`;
  if (o && st.accounts.some(x => x.pubkey !== a.pubkey && canSign(x))) h += `<button class="btn" data-a="copypick" data-x="${esc(e.d)}">${T("copy_to")}</button>`;
  if (canSign(a)) h += `<button class="btn d" data-a="delask" data-x="${esc(e.d)}">${T("delete")}</button>`;
  h += `</div>`;
  if (e.enc !== "nip44" && e.enc !== "empty") h += `<p class="small muted">${T("edit_only_nip44")}</p>`;
  return h;
}
function edit(sn, r) {
  const a = acc(), e = sn.entries.find(x => x.d === r.d), o = e && st.opened[okey(a.pubkey, e.d)];
  if (!o) return `<p class="muted">${T("item_gone")}</p>`;
  if (st.edit == null || st.edit.d !== e.d) st.edit = { d: e.d, text: pretty(o.text), err: "" };
  return `<p class="small muted mono">${esc(e.d)}</p><textarea id="edit-ta" class="in" data-in="edit" spellcheck="false">${esc(st.edit.text)}</textarea>
    ${st.edit.err ? `<div class="err">${esc(st.edit.err)}</div>` : ""}
    <div class="acts" style="justify-content:flex-end"><button class="btn" data-a="back">${T("cancel")}</button><button class="btn p" data-a="saveask" data-x="${esc(e.d)}">${T("save")}</button></div>`;
}
const tagRow = (t, actions = "") =>
  `<div class="tagrow"><span class="tag">${esc(t[0])}</span><div class="v"><div class="mono">${esc(t[1])}</div>${t.length > 2 ? `<div class="small muted">${esc(t.slice(2).join(" · "))}</div>` : ""}</div>${actions}</div>`;
function list(sn, r) {
  const a = acc(), e = sn.lists.find(x => address(x) === r.addr);
  if (!e) return `<p class="muted">${T("item_gone")}</p>`;
  const priv = st.listPriv[r.addr], pub = publicItems(e);
  let h = `<div class="group"><dl class="info"><dt>kind</dt><dd>${e.kind}</dd>${tag(e, "d") ? `<dt>d</dt><dd class="mono">${esc(tag(e, "d"))}</dd>` : ""}<dt>${T("info_updated")}</dt><dd>${fullDate(e.created_at)}</dd></dl></div>`;
  const d = st.draft?.addr === r.addr ? st.draft : null;
  if (!d) {
    h += `<div class="group"><b>${T("list_public", pub.length)}</b>${pub.length ? pub.map(t => tagRow(t)).join("") : `<div class="small muted">${T("none")}</div>`}</div>`;
    if (e.content) {
      h += `<div class="group"><b>🔒 ${T("list_private")}</b> ${priv ? `<span class="badge">${priv.length}</span>` : ""}`;
      if (priv) h += priv.length ? priv.map(t => tagRow(t)).join("") : `<div class="small muted">${T("none")}</div>`;
      else if (!canSign(a)) h += `<div class="small muted">${T("readonly_cannot_decrypt")}</div>`;
      else h += `<div style="margin-top:8px"><button class="btn p" data-a="openlist" data-x="${esc(r.addr)}">${T("decrypt")}</button></div>`;
      h += `</div>`;
    }
    if (canSign(a)) h += `<div class="acts"><button class="btn" data-a="listedit" data-x="${esc(r.addr)}">${T("edit")}</button></div>`;
    h += `<p class="small muted">${T("lists_edit_note")}</p>`;
    return h;
  }
  h += `<p class="small muted">${T("lists_edit_rules")}</p>`;
  for (const isPriv of [false, true]) {
    const rows = d.items.map((it, i) => [it, i]).filter(([it]) => it.priv === isPriv);
    h += `<div class="group"><div style="display:flex;align-items:center"><b style="flex:1">${isPriv ? "🔒 " + T("list_private") : T("list_public", rows.length)}</b>${isPriv ? `<button class="btn t" data-a="itemdlg" data-x="-1">+ ${T("add")}</button>` : ""}</div>`;
    h += rows.length ? rows.map(([it, i]) => tagRow(it.tag,
      (isPriv ? `<button class="btn t" data-a="itemdlg" data-x="${i}">${T("edit")}</button>` : `<button class="btn t" data-a="mkpriv" data-x="${i}">${T("make_private")}</button>`)
      + `<button class="icon-btn" data-a="rmitem" data-x="${i}" aria-label="${T("remove")}">✕</button>`)).join("") : `<div class="small muted">${T("none")}</div>`;
    h += `</div>`;
  }
  h += `<div class="acts" style="justify-content:flex-end"><button class="btn" data-a="listcancel">${T("cancel")}</button><button class="btn p" data-a="listsaveask">${T("save")}</button></div>`;
  return h;
}

// ---------- Ajustes ----------
function settings(sec) {
  if (!sec) {
    const blocks = [["accounts"], ["appearance", "relays"], ["trash"], ["about"]];
    const icon = { accounts: "👥", appearance: "🎨", relays: "🛰", trash: "🗑", about: "ℹ" };
    const sum = {
      accounts: TP("accounts_count", st.accounts.length),
      appearance: `${T("theme_" + (st.settings.theme === "system" ? "system" : st.settings.theme))} · ${T("accent_" + st.settings.accent)} · ${{ ca: "Català", es: "Español", en: "English" }[LS.get(P + "lang", null)] || T("lang_system")}`,
      relays: TP("relays_count", appRelays().length), trash: TP("trash_count", trashCache.length), about: T("version", VERSION),
    };
    const name = { accounts: "accounts", appearance: "settings_appearance", relays: "settings_relays", trash: "trash", about: "settings_about" };
    return blocks.map(b => `<div class="group" style="padding:4px 8px">${b.map((s, i) => `${i ? '<hr class="divider">' : ""}<button class="row" data-a="sec" data-x="${s}"><span class="ico">${icon[s]}</span><span class="t"><div class="n">${T(name[s])}</div><div class="m">${esc(sum[s])}</div></span><span class="chev">›</span></button>`).join("")}</div>`).join("");
  }
  if (sec === "accounts") return `<div class="group" style="padding:4px 8px">${st.accounts.map((a, i) => `${i ? '<hr class="divider">' : ""}<div class="row click" data-a="setactive" data-x="${a.pubkey}">${avatar(a, "l")}<span class="t"><div class="n">${esc(nameOf(a))}${a.pubkey === acc()?.pubkey ? " ✓" : ""}</div><div class="m">${shortNpub(a.pubkey)} · ${methodLabel(a.method)}</div><div><button class="btn t" data-a="renameask" data-x="${a.pubkey}">${T("rename")}</button><button class="btn t danger" data-a="removeask" data-x="${a.pubkey}">${T("remove")}</button></div></span></div>`).join("")}</div>
    <div class="sec">${T("account_add")}</div>${loginButtons()}<p class="small muted">${T("accounts_note")} ${T("web_storage_note")}</p>`;
  if (sec === "appearance") {
    const s = st.settings, cl = LS.get(P + "lang", null);
    return `<div class="group"><b>${T("settings_theme")}</b><div class="seg">${["light", "dark", "system"].map(m => `<button class="${s.theme === m ? "on" : ""}" data-a="theme" data-x="${m}">${T("theme_" + m)}</button>`).join("")}</div></div>
      <div class="group"><b>${T("settings_accent")}</b><div class="small muted">${T("accent_" + s.accent)}</div><div class="swatches">${Object.keys(ACCENTS).map(k => `<button class="sw ${s.accent === k ? "on" : ""}" style="background:${ACCENTS[k][0]}" data-a="accent" data-x="${k}" aria-label="${T("accent_" + k)}"></button>`).join("")}</div></div>
      <div class="sec">${T("settings_language")}</div><div class="group">${[[null, T("lang_system")], ["ca", "Català"], ["es", "Español"], ["en", "English"]].map(([t, l]) => `<button class="radio ${cl === t ? "on" : ""}" data-a="lang" data-x="${t || ""}"><i></i>${l}</button>`).join("")}<p class="small muted">${T("lang_note")}</p></div>`;
  }
  if (sec === "relays") {
    const mine = appRelays(), sn = snap();
    return `<div class="group"><b>${T("relays_app")}</b><p class="small muted">${T("relays_app_sub")}</p>${mine.map(u => `<div class="tagrow"><div class="v mono">${esc(u)}</div>${mine.length > 1 ? `<button class="icon-btn" data-a="rmrelay" data-x="${esc(u)}" aria-label="${esc(T("relay_remove", u))}">✕</button>` : ""}</div>`).join("")}
      <div style="display:flex;gap:8px;margin-top:8px"><input id="relay-in" class="in mono" placeholder="wss://relay.example.com" autocapitalize="off"><button class="btn" data-a="addrelay">${T("add")}</button></div><div id="relay-err" class="err"></div>
      ${st.settings.relays ? `<button class="btn t" data-a="resetrelays">${T("relays_restore")}</button>` : ""}</div>
      ${sn ? `<div class="group"><b>${T("relays_account")}</b><p class="small muted">${T("relays_account_sub")}</p>${sn.relays.map(u => `<div class="mono small">${esc(u)}</div>`).join("")}</div>` : ""}`;
  }
  if (sec === "trash") {
    let h = `<p class="small muted">${T("trash_note")} ${T("web_storage_note")}</p>`;
    if (!trashCache.length) return h + `<p class="muted">${T("trash_empty")}</p>`;
    h += `<div class="group" style="padding:4px 12px">${trashCache.map((t, i) => { const o = st.accounts.find(a => a.pubkey === t.pubkey); return `${i ? '<hr class="divider">' : ""}<div class="row"><span class="t"><div class="n mono">${esc(t.d)}</div><div class="m">${esc((o ? nameOf(o) : shortNpub(t.pubkey)) + " · " + ago(Math.floor(t.at / 1000)))}</div></span><button class="btn t" data-a="restoreask" data-x="${esc(t.id)}" ${canSign(o) && t.events.every(e => encoding(e.content) !== "plain") ? "" : "disabled"}>${T("restore")}</button><button class="icon-btn" data-a="rmtrash" data-x="${esc(t.id)}" aria-label="${T("remove")}">✕</button></div>`; }).join("")}</div>`;
    return h + `<button class="btn" data-a="cleartrash">${T("trash_clear")}</button>`;
  }
  if (sec === "about") {
    return `<div class="center">${LOGO.replace('class="logo"', 'class="logo" style="width:72px;height:72px;border-radius:18px"')}<h2 style="margin:10px 0 2px">Tansu</h2><div class="muted">${T("version", VERSION)}</div><p class="small muted">${T("about_ai")}</p></div>
      ${st.install ? `<div class="group"><button class="btn p block" data-a="install">${T("install_app")}</button><p class="small muted">${T("install_note")}</p></div>` : ""}
      <div class="group" style="padding:4px 8px">
        <a class="row" href="${REPO}" target="_blank" rel="noopener"><span class="ico">&lt;/&gt;</span><span class="t"><div class="n">${T("about_source")}</div><div class="m">github.com/oluengo/tansu</div></span>↗</a>
        <a class="row" href="${REPO}/releases" target="_blank" rel="noopener"><span class="ico">🤖</span><span class="t"><div class="n">${T("android_app")}</div><div class="m">${T("android_app_sub")}</div></span>↗</a>
        <a class="row" href="${REPO}/issues/new?labels=bug" target="_blank" rel="noopener"><span class="ico">🐞</span><span class="t"><div class="n">${T("about_bug")}</div><div class="m">${T("about_bug_sub")}</div></span>↗</a>
        <a class="row" href="${REPO}/issues/new?labels=enhancement" target="_blank" rel="noopener"><span class="ico">💡</span><span class="t"><div class="n">${T("about_idea")}</div><div class="m">${T("about_idea_sub")}</div></span>↗</a>
      </div>
      <div class="group"><b>${T("about_license_title")}</b><p class="small">${T("about_license_body")}</p><p class="small">${T("about_warranty")}</p>${lang !== "en" ? `<p class="small muted">${T("about_legal_english")}</p>` : ""}</div>
      <div class="sec">${T("credits_title")}</div>
      <div class="group" style="padding:4px 8px"><a class="row" href="${NOSTRICH}" target="_blank" rel="noopener">${LOGO.replace('class="logo"', 'class="logo" style="width:36px;height:36px"')}<span class="t"><div class="n">${T("credits_nostrich")}</div><div class="m">${T("credits_nostrich_sub")}</div></span>↗</a></div>
      <div class="sec">${T("support_title")}</div>
      <div class="group"><p class="small">${T("support_body")}</p>
        <button class="row" data-a="copytext" data-x="${BTC}"><span class="ico" style="background:#F7931A;color:#fff">₿</span><span class="t"><div class="n">Bitcoin</div><div class="m mono">${BTC.slice(0, 12)}…${BTC.slice(-6)}</div></span></button>
        <button class="row" data-a="copytext" data-x="${LN}"><span class="ico" style="background:#7B3FE4;color:#fff">⚡</span><span class="t"><div class="n">Lightning · zap</div><div class="m mono">${LN}</div></span></button></div>`;
  }
  return "";
}
function loginButtons() {
  return `<div class="btns" style="display:flex;flex-direction:column;gap:8px">
    ${isAndroid ? `<button class="btn p block" data-a="loginamber">${T("login_amber")}</button>` : ""}
    <button class="btn ${isAndroid ? "" : "p"} block" data-a="loginnip07">${T("login_nip07")}</button>
    <button class="btn block" data-a="bunkerdlg">${T("login_bunker")}</button>
    <button class="btn block" data-a="npubdlg">${T("login_npub")}</button>
    ${isAndroid ? `<p class="small muted">${T("amber_web_note")}</p>` : ""}</div>`;
}
function welcome() {
  return `<div class="welcome"><img class="logo" src="icon.svg" alt=""><h1 style="margin:6px 0 0">${BETA ? "Tansu Beta" : "Tansu"}</h1>
    <p class="muted" style="max-width:420px">${T("welcome_body")}</p><p class="small muted" style="max-width:420px">${T("privacy_note")}</p>
    ${loginButtons().replace('class="btns"', 'class="btns"')}
    ${st.install ? `<button class="btn t" data-a="install">${T("install_app")}</button>` : ""}</div>`;
}

// ---------- Capas (hojas y diálogos) ----------
function sheet(h) { $("#layer").innerHTML = `<div class="shade" data-a="shade"><div class="sheet">${h}</div></div>`; }
function dialog(h) { $("#layer").innerHTML = `<div class="shade mid" data-a="shade"><div class="dialog">${h}</div></div>`; }
function closeLayer() { $("#layer").innerHTML = ""; }
function confirmDlg(titleT, body, okLabel, onOk, danger) {
  dialog(`<h3>${esc(titleT)}</h3><p>${esc(body)}</p><div class="acts"><button class="btn" data-a="close">${T("cancel")}</button><button class="btn ${danger ? "d" : "p"}" data-a="ok">${esc(okLabel)}</button></div>`);
  pendingOk = onOk;
}
let pendingOk = null;
function itemDialog(idx) {
  const d = st.draft, e = snap().lists.find(x => address(x) === d.addr), init = idx >= 0 ? d.items[idx].tag : null;
  const sug = SUGGESTED(e.kind), name = init?.[0] || sug[0];
  dialog(`<h3>${T(init ? "item_edit" : "item_add")}</h3>
    <div class="chips">${[...new Set([...sug, name])].map(t => `<button class="${t === name ? "on" : ""}" data-a="pickTag" data-x="${esc(t)}">${esc(t)}</button>`).join("")}</div>
    <label class="field"><span>${T("item_tag")}</span><input id="it-tag" class="in mono" value="${esc(name)}" autocapitalize="off"></label>
    <label class="field"><span>${T("item_value")}</span><input id="it-val" class="in mono" value="${esc(init?.[1] || "")}" autocapitalize="off"></label>
    <label class="field"><span>${T("item_extra")}</span><input id="it-extra" class="in" value="${esc(init ? init.slice(2).join(" · ") : "")}"></label>
    <div id="it-err" class="err"></div>
    <div class="acts"><button class="btn" data-a="close">${T("cancel")}</button><button class="btn p" data-a="itemsave" data-x="${idx}">${T("save")}</button></div>`);
}

// ---------- Acciones ----------
const ACT = {
  back, refresh, close: closeLayer,
  shade(el, ev) { if (ev.target === el) closeLayer(); },
  ok() { const f = pendingOk; pendingOk = null; closeLayer(); f?.(); },
  go(el) { go(JSON.parse(el.dataset.x)); },
  sec(el) { go({ v: "settings", sec: el.dataset.x }); },
  clearq() { st.q = ""; render(); },
  view(el) { LS.set(P + "view", el.dataset.x); render(); },
  jvrows(el) { document.querySelectorAll("." + el.dataset.x).forEach(r => r.hidden = false); el.remove(); },
  showall() { cur().all = true; render(); },
  accounts() {
    sheet(`<h3>${T("accounts")}</h3>${st.accounts.map(a => `<button class="row" data-a="pickacc" data-x="${a.pubkey}">${avatar(a, "l")}<span class="t"><div class="n">${esc(nameOf(a))}</div><div class="m">${shortNpub(a.pubkey)} · ${methodLabel(a.method)}</div></span>${a.pubkey === acc()?.pubkey ? "✓" : ""}</button>`).join("")}
      <div class="sec">${T("account_add")}</div>${loginButtons()}`);
  },
  pickacc(el) { closeLayer(); setActive(el.dataset.x); },
  setactive(el, ev) { if (ev.target.closest("button")) return; setActive(el.dataset.x); },
  loginamber() { closeLayer(); loginAmber(); },
  nccancel() { ncAbort?.abort(); closeLayer(); },
  loginnip07() { closeLayer(); loginNip07(); },
  bunkerdlg() {
    dialog(`<h3>${T("login_bunker")}</h3><p class="small">${T("bunker_explain")}</p><input id="bunker-in" class="in mono" placeholder="bunker://…" autocapitalize="off"><div id="bunker-err" class="err"></div>
      <div class="acts"><button class="btn" data-a="close">${T("cancel")}</button><button id="bunker-go" class="btn p" data-a="bunkergo">${T("bunker_connect")}</button></div>`);
    $("#bunker-in").focus();
  },
  bunkergo: loginBunker,
  npubdlg() {
    dialog(`<h3>${T("login_npub")}</h3><p class="small">${T("npub_explain")}</p><input id="npub-in" class="in mono" placeholder="npub1…" autocapitalize="off"><div id="npub-err" class="err"></div>
      <div class="acts"><button class="btn" data-a="close">${T("cancel")}</button><button class="btn p" data-a="npubgo">${T("add")}</button></div>`);
    $("#npub-in").focus();
  },
  npubgo: loginNpub,
  open(el) { run("open", { d: el.dataset.x }); },
  openlist(el) { run("openList", { addr: el.dataset.x }); },
  export(el) {
    const a = acc(), e = snap().entries.find(x => x.d === el.dataset.x), o = st.opened[okey(a.pubkey, e.d)];
    const text = o?.text ?? e.main.content, blob = new Blob([text], { type: isJson(text) ? "application/json" : "text/plain" });
    const url = URL.createObjectURL(blob), l = document.createElement("a");
    l.href = url; l.download = e.d.replace(/\//g, "-") + (isJson(text) ? ".json" : ".txt"); document.body.appendChild(l); l.click(); l.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000); toast(T("exported"));
  },
  copypick(el) {
    const d = el.dataset.x, a = acc();
    sheet(`<h3>${T("copy_to_title")}</h3>${st.accounts.filter(x => x.pubkey !== a.pubkey && canSign(x)).map(t => `<button class="row" data-a="copygo" data-x='${J({ d, t: t.pubkey })}'>${avatar(t, "l")}<span class="t"><div class="n">${esc(nameOf(t))}</div><div class="m">${methodLabel(t.method)}</div></span></button>`).join("")}`);
  },
  copygo(el) {
    const { d, t } = JSON.parse(el.dataset.x), ta = st.accounts.find(x => x.pubkey === t);
    confirmDlg(T("copy_confirm_title", nameOf(ta)), T("copy_confirm_body", d), T("copy"), () => run("copy", { d, target: t }));
  },
  delask(el) { const d = el.dataset.x; confirmDlg(T("delete_title", d), T("delete_body"), T("delete"), () => run("del", { d }), true); },
  saveask(el) {
    const d = el.dataset.x, a = acc(), e = snap().entries.find(x => x.d === d), o = st.opened[okey(a.pubkey, d)];
    let text = st.edit.text;
    if (isJson(o.text)) { try { text = JSON.stringify(JSON.parse(text)); } catch (err) { st.edit.err = T("json_invalid") + " " + err.message; render(); return; } }
    confirmDlg(T("save_title"), T("save_body") + (e.parts.length ? "\n\n" + T("save_body_parts") : ""), T("save"), () => run("save", { d, text, manifest: o.manifest }));
  },
  listedit(el) {
    const addr = el.dataset.x, e = snap().lists.find(x => address(x) === addr);
    const start = () => { st.draft = { addr, items: [...publicItems(e).map(t => ({ tag: t, priv: false })), ...(st.listPriv[addr] || []).map(t => ({ tag: t, priv: true }))] }; render(); };
    if (e.content && !st.listPriv[addr]) run("openList", { addr }).then(ok => ok && start()); else start();
  },
  listcancel() { st.draft = null; render(); },
  mkpriv(el) { st.draft.items[+el.dataset.x].priv = true; render(); },
  rmitem(el) { st.draft.items.splice(+el.dataset.x, 1); render(); },
  itemdlg(el) { itemDialog(+el.dataset.x); },
  pickTag(el) { $("#it-tag").value = el.dataset.x; document.querySelectorAll(".chips button").forEach(b => b.classList.toggle("on", b === el)); },
  itemsave(el) {
    const idx = +el.dataset.x, name = $("#it-tag").value.trim(), v = $("#it-val").value.trim(), err = $("#it-err");
    if (!name) return;
    let val = v;
    if (!v) { err.textContent = T("value_empty"); return; }
    if (name === "p") { val = N.toHex(v); if (!val) { err.textContent = T("npub_invalid"); return; } }
    if (name === "relay") { val = normRelay(v); if (!val) { err.textContent = T("relay_invalid"); return; } }
    const t = [name, val, ...$("#it-extra").value.split("·").map(x => x.trim()).filter(Boolean)];
    if (idx >= 0) st.draft.items[idx] = { tag: t, priv: true }; else st.draft.items.push({ tag: t, priv: true });
    closeLayer(); render();
  },
  listsaveask() {
    const d = st.draft;
    confirmDlg(T("save_title"), T("save_list_body"), T("save"), () => run("saveList", { addr: d.addr, pub: d.items.filter(i => !i.priv).map(i => i.tag), priv: d.items.filter(i => i.priv).map(i => i.tag) }));
  },
  renameask(el) {
    const a = st.accounts.find(x => x.pubkey === el.dataset.x);
    dialog(`<h3>${T("rename")}</h3><input id="ren-in" class="in" value="${esc(a.label || "")}" placeholder="${esc(st.profiles[a.pubkey]?.name || shortNpub(a.pubkey))}"><div class="acts"><button class="btn" data-a="close">${T("cancel")}</button><button class="btn p" data-a="renamego" data-x="${a.pubkey}">${T("save")}</button></div>`);
  },
  renamego(el) { const a = st.accounts.find(x => x.pubkey === el.dataset.x); a.label = $("#ren-in").value.trim() || undefined; saveAccounts(); closeLayer(); render(); },
  removeask(el) { const a = st.accounts.find(x => x.pubkey === el.dataset.x); confirmDlg(T("remove_account_title", nameOf(a)), T("remove_account_body"), T("remove"), () => removeAccount(a.pubkey), true); },
  theme(el) { st.settings.theme = el.dataset.x; saveSettings(); },
  accent(el) { st.settings.accent = el.dataset.x; saveSettings(); },
  lang(el) { const t = el.dataset.x || null; if (t) LS.set(P + "lang", t); else LS.del(P + "lang"); lang = pickLang(); render(); },
  addrelay() { const u = normRelay($("#relay-in").value); if (!u) { $("#relay-err").textContent = T("relay_invalid"); return; } const l = appRelays(); if (!l.includes(u)) st.settings.relays = [...l, u]; saveSettings(); refresh(); },
  rmrelay(el) { st.settings.relays = appRelays().filter(u => u !== el.dataset.x); saveSettings(); refresh(); },
  resetrelays() { st.settings.relays = null; saveSettings(); refresh(); },
  restoreask(el) { const t = trashCache.find(x => x.id === el.dataset.x); confirmDlg(T("restore_title", t.d), T("restore_body"), T("restore"), () => run("restore", { id: t.id }).then(render)); },
  async rmtrash(el) { await DB.del(el.dataset.x); await loadTrash(); render(); },
  cleartrash() { confirmDlg(T("trash_clear"), T("trash_clear_body"), T("trash_clear"), async () => { await DB.clear(); await loadTrash(); render(); }, true); },
  async copytext(el) { try { await navigator.clipboard.writeText(el.dataset.x); toast(T("copied_ok")); } catch { toast(el.dataset.x, 6000); } },
  async install() { const p = st.install; if (!p) return; st.install = null; p.prompt(); try { await p.userChoice; } catch {} render(); },
};
document.addEventListener("click", ev => {
  const el = ev.target.closest("[data-a]"); if (!el) return;
  const f = ACT[el.dataset.a]; if (!f) return;
  if (el.dataset.a !== "shade" && el.dataset.a !== "setactive") ev.preventDefault();
  f(el, ev);
});
document.addEventListener("input", ev => {
  const k = ev.target.dataset?.in; if (!k) return;
  if (k === "q") { st.q = ev.target.value; render(); }
  if (k === "edit") { st.edit.text = ev.target.value; st.edit.err = ""; }
});
document.addEventListener("keydown", ev => {
  if (ev.key === "Escape" && $("#layer").innerHTML) closeLayer();
  if (ev.key === "Enter" && ev.target.id === "bunker-in") loginBunker();
  if (ev.key === "Enter" && ev.target.id === "npub-in") loginNpub();
  if (ev.key === "Enter" && ev.target.id === "relay-in") ACT.addrelay();
});
matchMedia("(prefers-color-scheme: dark)").addEventListener?.("change", () => st.settings.theme === "system" && render());
addEventListener("beforeinstallprompt", e => { e.preventDefault(); st.install = e; render(); });

// ---------- Arranque ----------
(async function start() {
  lang = pickLang();
  if ("serviceWorker" in navigator && location.protocol === "https:") navigator.serviceWorker.register("sw.js").catch(() => {});
  // Limpieza: restos de la versión anterior y cualquier resultado que viniera en la dirección.
  LEGACY.forEach(k => LS.del(k));
  if (location.search) history.replaceState(null, "", location.pathname);
  // Cuentas de Amber de la versión anterior (sin conexión NIP-46): hay que volver a añadirlas.
  if (st.accounts.some(a => a.method === "amber" && !a.bp)) setTimeout(() => toast(T("amber_readd"), 7000), 600);
  loadTrash().then(render);
  render();
  if (!st.accounts.length) return;
  loadProfiles();
  if (acc()) refresh();
})();
