# 0001 · Tansu only publishes encrypted data

*Status: accepted · 7 Nov 2026*

## Decision

Tansu is for managing backups and private data. **Everything it publishes is encrypted with NIP-44 for the account itself.**

- Editing is only allowed on data already encrypted with NIP-44. Unencrypted data can be viewed (with a warning) but not touched.
- Before publishing, it checks that the signer returned NIP-44 text; if not, nothing is published.
- Deleting is a NIP-09 request (kind 5), which has no content.
- NIP-51 lists (from 0.2): anything added or edited always goes to the private part, encrypted with NIP-44. Existing public items can only be removed or made private; if a saved list would add something public, it isn't published. If the list had a private part, it must be decrypted before editing so it isn't lost.

## Reason

A mistake when saving must not leave a backup in the open. Nobody else needs to read this data.

## What would change it

If a public list ever needed to be published on purpose (for example, a shared bookmarks folder), it would be a separate, explicit action.
