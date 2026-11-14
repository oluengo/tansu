# 0002 · Several accounts and a local trash

*Status: accepted · 7 Nov 2026*

## Decision

- Several accounts can be added (Amber, bunker or read-only npub) and you can switch between them. The pubkey and how to sign are stored; each bunker connection is encrypted with the Android Keystore. An nsec is never stored.
- "Copy to another account" decrypts with the source account and re-encrypts for the destination, with the same `d` tag.
- Before deleting or overwriting, the original (encrypted) events are kept in a local trash. Restoring republishes them with a new date: relays reject the old version after a deletion, but the content is still valid because it's encrypted for the same account.

## Limits

- NIP-09 is a request: some relay may keep a copy.
- The trash never leaves the phone (no Android backup).
