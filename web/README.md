# Tansu web

La misma app en el navegador, con PWA opcional (se puede instalar; sin instalar funciona igual).

- **Cuentas:** Amber (en Android, como firmante remoto NIP-46 con `nostrconnect://`), extensión del navegador (NIP-07), bunker (NIP-46) o npub en solo lectura.
- **Nada descifrado en el navegador:** lo descifrado solo vive en memoria; no pasa por la dirección de la página ni por localStorage/IndexedDB. (No se usa NIP-55 por enlace porque devuelve el resultado en la URL, que queda en el historial.)
- **Privado:** igual que en Android, todo lo que se publica va cifrado con NIP-44 para la propia cuenta.
- Las cuentas y la papelera se guardan solo en ese navegador (localStorage e IndexedDB).

Publicada por `.github/workflows/pages.yml`: `main` → `oluengo.github.io/tansu/app/`, `beta` → `oluengo.github.io/tansu/beta/`. La página de presentación (`site/`) va en `oluengo.github.io/tansu/`.

## Archivos

| Archivo | Qué es |
|---|---|
| `index.html`, `app.js`, `app.css` | La app (JavaScript sin dependencias ni compilación) |
| `i18n.js` | Cadenas ca/es/en, generadas con `python3 scripts/strings/gen.py` desde las mismas de Android |
| `nostr.js` | nostr-tools empaquetado (`cd web/lib && npm ci && npm run build`) |
| `sw.js`, `manifest.webmanifest` | PWA |
| `tests/e2e.mjs` | Prueba con relays en memoria y una extensión NIP-07 de prueba (Playwright) |
