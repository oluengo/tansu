# Changelog

## 0.3.2 (beta)

- Where each item lives: every file and list shows its state on each of the account's relays (up to date, older version, incomplete, not there, no answer), and the list warns when an item is not up to date everywhere (e.g. "1/2 relays").
- "Copy to the ones missing it": sends the same event, already signed and encrypted, to the relays that lack it or have an older version. Nothing new is signed. Android and web.

## 0.3.1 (beta)

- Web: Amber connects as a remote signer (NIP-46, `nostrconnect://` link) instead of NIP-55 via link. Before, the decrypted text came back in the page address and could stay in the browser history (and get synced). Now decrypted content only lives in memory. Amber accounts added on the web need to be added again; leftovers from the previous version are cleared on opening.
- Documentation: security section (what encryption protects and what it doesn't).

## 0.3.0 (beta)

- Content with two views: **Visual** (cards, folder trees, tables, tags, readable dates, yes/no; generic for any app) and **Code** (the raw JSON, which is what you edit). Visual by default; the last one chosen is remembered. Android and web.

## 0.2.0 (beta)

- Edit NIP-51 lists: add and edit private items (encrypted with NIP-44), remove public ones or make them private. Nothing public is ever added; the previous version goes to the trash.
- Web version with optional PWA: Amber (Android), NIP-07 extension, bunker or npub; the same features as the app.
- Tansu's own signing key (fingerprint in Settings → About and in the README).

## 0.1.0 (beta)

- First version: several accounts (Amber, bunker, npub), app data explorer (NIP-78) by folders, Usus backups in parts, decrypt, edit, export, copy to another account, delete and local trash.
- NIP-51 lists, read-only (including the private part).
- Settings: accounts, appearance and language (ca/es/en), relays, trash and About with credits.
