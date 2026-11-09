<p align="center"><img src="docs/brand/tansu-icon.png" width="96" alt="Tansu"></p>

<h1 align="center">Tansu</h1>

<p align="center"><b>Tus datos privados de Nostr, en carpetas.</b><br><i>Your private Nostr data, in folders.</i></p>

<p align="center"><a href="https://oluengo.github.io/tansu/">Web</a> · <a href="https://oluengo.github.io/tansu/app/">Abrir la app</a> · <a href="https://github.com/oluengo/tansu/releases/tag/tansu-beta">Descargar para Android</a> · <a href="#english">English</a></p>

**Tansu** es un explorador de lo que tus apps de Nostr guardan en tu cuenta: copias de seguridad y ajustes (NIP-78) y tus listas (NIP-51). Lo ves, lo editas y lo mueves entre cuentas, siempre cifrado. Para Android y en el navegador (PWA), con varias cuentas a la vez. Gratis, sin anuncios. En català, castellano e inglés.

> Estado: **beta**.

## Qué hace

- **Una carpeta por app:** Wavestr, Usus, Mado… agrupadas solas por el prefijo de la etiqueta `d` (`wavestr/favorites`, `usus/backup`).
- **Varias cuentas** (Amber, extensión del navegador, bunker o npub) y cambio rápido entre ellas.
- **Copias por partes** (formato de Usus: índice + partes comprimidas): se ven como un solo archivo.
- **Descifrar, ver y editar** (JSON validado), **exportar a archivo**, **copiar a otra cuenta** (se vuelve a cifrar para ella) y **eliminar** (NIP-09).
- **Papelera local:** antes de borrar o sobrescribir se guarda una copia cifrada para poder restaurarla.
- **Listas NIP-51:** ver (también la parte privada) y editar. Lo nuevo siempre va a la parte privada, cifrada.
- Tema claro, oscuro o automático, color de acento y relays editables.

## Instalar

| | Cómo |
|---|---|
| **Web y PWA** | Abre <https://oluengo.github.io/tansu/app/> y, si quieres, instálala desde el navegador. Entras con Amber (en Android), una extensión NIP-07, un bunker o tu npub. |
| **Android** | APK de la pre-release [Tansu Beta](https://github.com/oluengo/tansu/releases/tag/tansu-beta), o añade `https://github.com/oluengo/tansu` en [Obtainium](https://github.com/ImranR98/Obtainium) activando «incluir pre-releases». |
| **Beta web** | <https://oluengo.github.io/tansu/beta/>, con sus propios datos. |

Huella SHA-256 del certificado del APK (también en Ajustes → Acerca de): `E3:71:51:7A:B8:5F:4F:82:3C:D5:7D:79:9B:DF:11:72:E3:3D:C9:96:B2:FF:9D:4E:9B:F5:F6:BA:1E:D1:77:99`

## Privacidad

- **Nunca publica nada legible:** todo lo que Tansu escribe va cifrado con NIP-44 para la propia cuenta. Los datos sin cifrar se pueden ver, pero no editar.
- **Nunca ve tu nsec:** firma y cifra Amber (NIP-55), la extensión del navegador (NIP-07) o un bunker (NIP-46).
- Lo descifrado solo vive en memoria mientras la app está abierta. Sin servidor propio ni rastreadores: solo tus relays.

## Desarrollo

Kotlin + Jetpack Compose (`:core` y `:app`), sin dependencias de Nostr: cliente de relays, NIP-44, NIP-46 y firmas propios (vienen de Wavestr). La app web está en `web/` (JavaScript sin compilación).

- Plan y decisiones: [docs/plan-tecnico.md](docs/plan-tecnico.md) · App web: [web/README.md](web/README.md) · Maqueta: [demo/index.html](demo/index.html)

## Apoya el proyecto

Si Tansu te resulta útil:

- **Lightning / zaps:** `oluengo@rizful.com`
- **Bitcoin:** `bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa`
- **Nostr:** [npub1vuf2g6s0mcjr34lhkg2vvtj4x65w29xn28nznw9fhcqcpyr4zc9q6myguj](https://njump.me/npub1vuf2g6s0mcjr34lhkg2vvtj4x65w29xn28nznw9fhcqcpyr4zc9q6myguj)

---

[Novedades](CHANGELOG.md) · [Informar de un problema](https://github.com/oluengo/tansu/issues) · [Créditos](CREDITS.md) · [Licencia MIT](LICENSE)

<sub>Hecha con IA · probada por humanos.</sub>

---

<h2 id="english">English</h2>

**Tansu** is an explorer for what your Nostr apps keep in your account: backups and settings (NIP-78) and your lists (NIP-51). See it, edit it and move it between accounts, always encrypted. For Android and in the browser (PWA), with several accounts at once. Free, no ads. In Catalan, Spanish and English.

**[Website](https://oluengo.github.io/tansu/)** · **[Open the app](https://oluengo.github.io/tansu/app/)** · **[Download for Android](https://github.com/oluengo/tansu/releases/tag/tansu-beta)**

> Status: **beta**.

### What it does

- **A folder per app:** Wavestr, Usus, Mado… grouped automatically by the `d` tag prefix (`wavestr/favorites`, `usus/backup`).
- **Several accounts** (Amber, browser extension, bunker or npub) and quick switching.
- **Backups in parts** (Usus format: index + compressed parts) are shown as a single file.
- **Decrypt, view and edit** (validated JSON), **export to a file**, **copy to another account** (re-encrypted for it) and **delete** (NIP-09).
- **Local trash:** before deleting or overwriting, an encrypted copy is kept so you can restore it.
- **NIP-51 lists:** view (including the private part) and edit. Anything new always goes to the private, encrypted part.
- Light, dark or automatic theme, accent colour and editable relays.

### Install

| | How |
|---|---|
| **Web and PWA** | Open <https://oluengo.github.io/tansu/app/> and, if you like, install it from the browser. Sign in with Amber (on Android), a NIP-07 extension, a bunker or your npub. |
| **Android** | The APK from the [Tansu Beta](https://github.com/oluengo/tansu/releases/tag/tansu-beta) pre-release, or add `https://github.com/oluengo/tansu` to [Obtainium](https://github.com/ImranR98/Obtainium) with “include pre-releases” on. |
| **Web beta** | <https://oluengo.github.io/tansu/beta/>, with its own data. |

APK certificate SHA-256 fingerprint (also in Settings → About): `E3:71:51:7A:B8:5F:4F:82:3C:D5:7D:79:9B:DF:11:72:E3:3D:C9:96:B2:FF:9D:4E:9B:F5:F6:BA:1E:D1:77:99`

### Privacy

- **Never publishes anything readable:** everything Tansu writes is encrypted with NIP-44 for the account itself. Unencrypted data can be viewed but not edited.
- **Never sees your nsec:** Amber (NIP-55), the browser extension (NIP-07) or a bunker (NIP-46) sign and encrypt.
- Decrypted content only lives in memory while the app is open. No servers of its own, no trackers: just your relays.

### Development

Kotlin + Jetpack Compose (`:core` and `:app`), with no Nostr dependencies: its own relay client, NIP-44, NIP-46 and signatures (taken from Wavestr). The web app lives in `web/` (plain JavaScript, no build step).

- Plan and decisions (in Spanish): [docs/plan-tecnico.md](docs/plan-tecnico.md) · Web app: [web/README.md](web/README.md) · Mockup: [demo/index.html](demo/index.html)

### Support the project

If you find Tansu useful:

- **Lightning / zaps:** `oluengo@rizful.com`
- **Bitcoin:** `bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa`
- **Nostr:** [npub1vuf2g6s0mcjr34lhkg2vvtj4x65w29xn28nznw9fhcqcpyr4zc9q6myguj](https://njump.me/npub1vuf2g6s0mcjr34lhkg2vvtj4x65w29xn28nznw9fhcqcpyr4zc9q6myguj)

---

[Changelog](CHANGELOG.md) · [Report a problem](https://github.com/oluengo/tansu/issues) · [Credits](CREDITS.md) · [MIT License](LICENSE)

<sub>Built with AI · tested by humans.</sub>

Seguridad y privacidad (qué protege el cifrado y qué no): [docs/seguridad.md](docs/seguridad.md).
