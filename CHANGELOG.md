# Changelog

## [0.3.8]

- Group operations. Long-press an item or a whole folder (or tap the select button) to choose several items; the selection is kept while moving between folders, and "All" / "None" select a whole folder.
- Copy to relays: choose one or more relays; each says how many of the chosen items it is missing, and the ones that responded and are missing something come preselected (except relays rated 1–2★). Unencrypted items are skipped, as always.
- Delete from relays: each relay says how many it has; nothing is preselected ("Delete from all relays…" in More selects them all). One NIP-09 request per relay (in batches of 50 items), with a confirmation first and a warning when items would be left with no copy on any relay — those go to the trash first, encrypted as they were.
- Result per relay: copied or deleted, rejected (with the relay's own reason) and relays that ignored the deletion.
- Copy several app items to another account at once.
- Web: list addresses now follow NIP-01 ("kind:pubkey:" with the trailing colon), so deletion requests for lists match on every relay.

## [0.3.7]

First stable release. It brings together everything tested in the betas (0.1.0 to 0.3.6, below):

- Several Nostr accounts (Amber, bunker, browser extension on the web, or npub read-only). Tansu never sees your nsec.
- App data explorer (NIP-78) by folders, including multi-part Usus backups: decrypt, view (Visual or Code), edit, export, copy to another account, delete with a local trash.
- NIP-51 lists: view and edit the private part; nothing public is ever added.
- Only encrypted writes (NIP-44 for your own account): nothing readable is ever published.
- Where each item lives, relay by relay: copy this item or a chosen selection to a relay, delete from a specific relay.
- Relay info sheets (NIP-11), full relay list with filters, and reliability with stars.
- Android app and web version (optional PWA), in Catalan, Spanish and English.

New in this version:

- Relay reliability, with 1 to 5 stars (filled in colour = points, grey = missing) in the relay list, in each relay's sheet and when adding a relay. Four signals with fixed weights: availability 40% (NIP-66 monitors, kind 30166, plus your own history of loads), keeps your data 30%, used by people you follow 20% (their NIP-65 lists) and speed 10%. A missing signal shares its weight among the others ("provisional rating"); with fewer than two there are no stars. "How is it calculated?" shows the points of each signal.
- Backups first: if a relay has lost data of yours without you deleting it, it gets at most 3 stars and a warning ("do not use it as your only copy"). What you delete from a relay yourself does not count.
- New "Unreliable" filter in the relay list; summary line per relay (online, followed, % answered, lost).
- The history (whether each relay answered and which items it had, by address only, never content) stays on this device.

## [0.3.6] (beta)

- Copying to a relay no longer sends everything at once. The relay sheet, opened from an item, offers "Copy this item here" first, and "Choose what to copy here…" opens a picker.
- Picker built for accounts with a lot of data: only what is not up to date on that relay by default (or "All"), grouped by folder and collapsed, a checkbox per folder (all / some / none), "Show N more", search by name, and an always-visible summary (items, events, size). Coming from an item, that item is preselected and marked "where you are"; from "All relays", nothing is preselected.
- A multi-part backup (Usus) counts as one item ("12 parts · 480 kB") and is sent whole.
- Items that are not encrypted, or bigger than the relay's size limit (from its info sheet), can't be selected; the reason is shown.
- The buttons in an item's "Relays" block now say "this item", to make clear they copy nothing else.

## [0.3.5] (beta)

- Relay info sheets (NIP-11): tap a relay in an item's "Relays" block to see what it says about itself — free or paid (fees and payment page), login (NIP-42), restricted writes, size limits, supported NIPs, operator, contact, terms and software — plus its role in the account (NIP-65 or Tansu), response time and how many items are up to date, old or missing there, with "Copy here what is missing".
- "All relays": a full list with every relay of the account and its summary, filters (paid, login, no deletions, no answer) and "Add relay". From the home footer ("N relays") and Settings → Relays.
- Warnings under each relay in an item: too small for this copy (its size limit is below the item), does not announce deletions (NIP-09), and the last rejection reason the relay gave.
- What a relay says about itself is voluntary and not verified; Tansu only reads it.

## [0.3.4] (beta)

- Relays block: "Copy to the ones missing it" and "Copy to another relay…" are now stacked, full width and with the same style.

## [0.3.3] (beta)

- Per relay: "Copy here" for a relay that lacks an item or has an older version, and "Delete from this relay" (a NIP-09 deletion request sent only to that relay; the others do not change; a copy goes to the trash first).
- "Copy to another relay…": send an item, as it is (signed and encrypted), to any relay you type in, even one that is not in the account's list.

## [0.3.2] (beta)

- Where each item lives: every file and list shows its state on each of the account's relays (up to date, older version, incomplete, not there, no answer), and the list warns when an item is not up to date everywhere (e.g. "1/2 relays").
- "Copy to the ones missing it": sends the same event, already signed and encrypted, to the relays that lack it or have an older version. Nothing new is signed. Android and web.

## [0.3.1] (beta)

- Web: Amber connects as a remote signer (NIP-46, `nostrconnect://` link) instead of NIP-55 via link. Before, the decrypted text came back in the page address and could stay in the browser history (and get synced). Now decrypted content only lives in memory. Amber accounts added on the web need to be added again; leftovers from the previous version are cleared on opening.
- Documentation: security section (what encryption protects and what it doesn't).

## [0.3.0] (beta)

- Content with two views: **Visual** (cards, folder trees, tables, tags, readable dates, yes/no; generic for any app) and **Code** (the raw JSON, which is what you edit). Visual by default; the last one chosen is remembered. Android and web.

## [0.2.0] (beta)

- Edit NIP-51 lists: add and edit private items (encrypted with NIP-44), remove public ones or make them private. Nothing public is ever added; the previous version goes to the trash.
- Web version with optional PWA: Amber (Android), NIP-07 extension, bunker or npub; the same features as the app.
- Tansu's own signing key (fingerprint in Settings → About and in the README).

## [0.1.0] (beta)

- First version: several accounts (Amber, bunker, npub), app data explorer (NIP-78) by folders, Usus backups in parts, decrypt, edit, export, copy to another account, delete and local trash.
- NIP-51 lists, read-only (including the private part).
- Settings: accounts, appearance and language (ca/es/en), relays, trash and About with credits.
