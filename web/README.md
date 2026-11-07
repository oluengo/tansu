# Tansu web

La misma app en el navegador, con PWA opcional (se puede instalar; sin instalar funciona igual).

- **Cuentas:** Amber (en Android, por enlace NIP-55: cada paso abre Amber y vuelve), extensión del navegador (NIP-07), bunker (NIP-46) o npub en solo lectura.
- **Privado:** igual que en Android, todo lo que se publica va cifrado con NIP-44 para la propia cuenta.
- Las cuentas y la papelera se guardan solo en ese navegador (localStorage e IndexedDB).

Publicada por `.github/workflows/pages.yml`: `main` → `oluengo.github.io/tansu/`, `beta` → `oluengo.github.io/tansu/beta/`.

## Archivos

| Archivo | Qué es |
|---|---|
| `index.html`, `app.js`, `app.css` | La app (JavaScript sin dependencias ni compilación) |
| `i18n.js` | Cadenas ca/es/en, generadas con `python3 scripts/strings/gen.py` desde las mismas de Android |
| `nostr.js` | nostr-tools empaquetado (`cd web/lib && npm ci && npm run build`) |
| `sw.js`, `manifest.webmanifest` | PWA |
| `tests/e2e.mjs` | Prueba con relays en memoria y una extensión NIP-07 de prueba (Playwright) |
