<p align="center"><img src="docs/brand/tansu-icon.png" width="96" alt="Tansu"></p>

<h1 align="center">Tansu</h1>

<p align="center"><b>Your private Nostr data, in folders.</b></p>

<p align="center"><a href="https://oluengo.github.io/tansu/">Website</a> · <a href="https://oluengo.github.io/tansu/app/">Open the app</a> · <a href="https://github.com/oluengo/tansu/releases/tag/tansu-beta">Download for Android</a></p>

**Tansu** is an explorer for what your Nostr apps keep in your account: backups and settings (NIP-78) and your lists (NIP-51). See it, edit it and move it between accounts, always encrypted. For Android and in the browser (PWA), with several accounts at once. Free, no ads. In Catalan, Spanish and English.

> Status: **beta**.

## What it does

- **A folder per app:** Wavestr, Usus, Mado… grouped automatically by the `d` tag prefix (`wavestr/favorites`, `usus/backup`).
- **Several accounts** (Amber, browser extension, bunker or npub) and quick switching.
- **Backups in parts** (Usus format: index + compressed parts) are shown as a single file.
- **Decrypt, view and edit** (validated JSON), **export to a file**, **copy to another account** (re-encrypted for it) and **delete** (NIP-09).
- **Local trash:** before deleting or overwriting, an encrypted copy is kept so you can restore it.
- **NIP-51 lists:** view (including the private part) and edit. Anything new always goes to the private, encrypted part.
- Light, dark or automatic theme, accent colour and editable relays.

## Install

| | How |
|---|---|
| **Web and PWA** | Open <https://oluengo.github.io/tansu/app/> and, if you like, install it from the browser. Sign in with Amber (on Android), a NIP-07 extension, a bunker or your npub. |
| **Android** | The APK from the [Tansu Beta](https://github.com/oluengo/tansu/releases/tag/tansu-beta) pre-release, or add `https://github.com/oluengo/tansu` to [Obtainium](https://github.com/ImranR98/Obtainium) with “include pre-releases” on. |
| **Web beta** | <https://oluengo.github.io/tansu/beta/>, with its own data. |

APK certificate SHA-256 fingerprint (also in Settings → About): `E3:71:51:7A:B8:5F:4F:82:3C:D5:7D:79:9B:DF:11:72:E3:3D:C9:96:B2:FF:9D:4E:9B:F5:F6:BA:1E:D1:77:99`

## Privacy

- **Never publishes anything readable:** everything Tansu writes is encrypted with NIP-44 for the account itself. Unencrypted data can be viewed but not edited.
- **Never sees your nsec:** Amber (NIP-55), the browser extension (NIP-07) or a bunker (NIP-46) sign and encrypt.
- Decrypted content only lives in memory while the app is open. No servers of its own, no trackers: just your relays.

## Development

Kotlin + Jetpack Compose (`:core` and `:app`), with no Nostr dependencies: its own relay client, NIP-44, NIP-46 and signatures (taken from Wavestr). The web app lives in `web/` (plain JavaScript, no build step).

- Plan and decisions (in Spanish): [docs/plan-tecnico.md](docs/plan-tecnico.md) · Web app: [web/README.md](web/README.md) · Mockup: [demo/index.html](demo/index.html)

## Support the project

If you find Tansu useful:

- **Lightning / zaps:** `oluengo@rizful.com`
- **Bitcoin:** `bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa`
- **Nostr:** [npub1vuf2g6s0mcjr34lhkg2vvtj4x65w29xn28nznw9fhcqcpyr4zc9q6myguj](https://njump.me/npub1vuf2g6s0mcjr34lhkg2vvtj4x65w29xn28nznw9fhcqcpyr4zc9q6myguj)

---

[Changelog](CHANGELOG.md) · [Report a problem](https://github.com/oluengo/tansu/issues) · [Credits](CREDITS.md) · [MIT License](LICENSE)

<sub>Built with AI · tested by humans.</sub>

Seguridad y privacidad (qué protege el cifrado y qué no): [docs/seguridad.md](docs/seguridad.md).
