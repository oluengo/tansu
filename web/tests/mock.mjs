// Extensión NIP-07 de prueba y relays en memoria (solo para los tests).
import { finalizeEvent, getPublicKey } from "nostr-tools/pure";
import * as nip44 from "nostr-tools/nip44";
import { hexToBytes } from "nostr-tools/utils";
export function makeExt(skHex) {
  const sk = hexToBytes(skHex), pk = getPublicKey(sk);
  const ck = peer => nip44.v2.utils.getConversationKey(sk, peer);
  return {
    pubkey: pk,
    getPublicKey: async () => pk,
    signEvent: async t => finalizeEvent({ kind: t.kind, created_at: t.created_at, tags: t.tags, content: t.content }, sk),
    nip44: { encrypt: async (peer, text) => nip44.v2.encrypt(text, ck(peer)), decrypt: async (peer, c) => nip44.v2.decrypt(c, ck(peer)) },
    nip04: { decrypt: async () => { throw new Error("no"); } },
  };
}
export { finalizeEvent };
