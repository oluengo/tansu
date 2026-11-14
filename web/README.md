# Tansu web

The same app in the browser, with an optional PWA (it can be installed; it works the same without installing).

- **Accounts:** Amber (on Android, as a NIP-46 remote signer via `nostrconnect://`), browser extension (NIP-07), bunker (NIP-46) or a read-only npub.
- **Nothing decrypted is stored in the browser:** decrypted content only lives in memory; it never goes through the page address or localStorage/IndexedDB. (NIP-55 via link is not used because it returns the result in the URL, which stays in the history.)
- **Private:** as on Android, everything published is encrypted with NIP-44 for the account itself.
- Accounts and the trash are stored only in that browser (localStorage and IndexedDB).

Published by `.github/workflows/pages.yml`: `main` → `oluengo.github.io/tansu/app/`, `beta` → `oluengo.github.io/tansu/beta/`. The landing page (`site/`) goes to `oluengo.github.io/tansu/`.

## Files

| File | What it is |
|---|---|
| `index.html`, `app.js`, `app.css` | The app (JavaScript with no dependencies and no build step) |
| `i18n.js` | ca/es/en strings, generated with `python3 scripts/strings/gen.py` from the same ones as Android |
| `nostr.js` | Bundled nostr-tools (`cd web/lib && npm ci && npm run build`) |
| `sw.js`, `manifest.webmanifest` | PWA |
| `tests/e2e.mjs` | Test with in-memory relays and a test NIP-07 extension (Playwright) |
