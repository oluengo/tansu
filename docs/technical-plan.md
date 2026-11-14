# Tansu · Technical plan v0.1

*7 Nov 2026*

A file-manager-style explorer for the private data Nostr apps keep in your account. Android, multi-account, everything encrypted.

## Decisions

| Topic | Decision |
|---|---|
| Name | Tansu (Japanese chest of drawers). No Japanese characters in the brand |
| Icon | Indigo folder with SovrynMatt's Purple Nostrich (credits in About and CREDITS.md) |
| Privacy | Only publishes data encrypted with NIP-44 for the account itself (ADR 0001) |
| Accounts | Several: Amber, bunker, npub (ADR 0002) |
| Platform | Native Android (Kotlin, Compose, Material 3, minSdk 26) and web with optional PWA (JavaScript without a build step + nostr-tools) |
| Web: accounts | Amber as a remote signer (NIP-46, `nostrconnect://`), NIP-07 extension, bunker, npub. No NIP-55 via link: it returned the decrypted content in the URL |
| Base | Nostr client, crypto and styles from Wavestr |
| Languages | ca/es/en, default the device's or English |
| Distribution | GitHub Releases + Obtainium + Zapstore. `beta` branch → "Tansu Beta" |

## What is read

- **App data (NIP-78, kind 30078)** from the author. Grouped by the `d` prefix (`wavestr/…`, `usus/…`). If `d/0…d/N` exist besides `d`, it's a backup in parts (Usus format: JSON index + base64 gzip split into 40,000-character parts).
- **NIP-51 lists** (10000–10063 and 30000–30030), with their private part (NIP-44 or legacy NIP-04).
- Relays: each account's NIP-65 list plus Tansu's own.

## Operations

| Operation | How |
|---|---|
| Decrypt | The account's signer (Amber silently if permission was granted, otherwise it opens its screen; or bunker) |
| Edit | NIP-44 only. JSON validated and compacted. Backups in parts: index and parts are rebuilt; leftover parts are deleted |
| Export | JSON/text file with the decrypted content (a Usus backup can be imported into Usus) |
| Copy to another account | Re-encrypted for the destination account |
| Delete | NIP-09 (kind 5) with `e` and `a` of every event; a copy goes to the trash first |
| Restore | Republishes the encrypted events with a new date |
| Edit NIP-51 lists | Private: add, edit, remove (encrypted with NIP-44). Public: remove or make private. Nothing public is ever added |

## Phases

- **0.1 (beta):** all of the above; lists read-only.
- **0.2 (beta):** edit NIP-51 lists without making anything public. Tansu's own signing key.
- **Next:** create new private lists, import a file as a new entry, compare versions, warning about old backups.
