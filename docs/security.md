# Security and privacy in Tansu

*What encryption protects, what it doesn't, and what is better not to upload.*

## What is protected

- **Truly encrypted content.** What Tansu publishes is encrypted with NIP-44 (version 2) for the account itself. NIP-44 was audited by Cure53 in December 2023 (<https://cure53.de/audit-report_nip44-implementations.pdf>). Relays store the ciphertext; they can't read it.
- **Tansu doesn't have your key.** Your signer (Amber, bunker or extension) signs, encrypts and decrypts. Tansu never sees or stores the nsec.
- **Decrypted content only in memory.** Neither the app nor the web app stores it on disk. The trash keeps the events as they are (encrypted). Only "Export file" creates a decrypted file, because you ask for it.
- **Nothing readable is ever published** (see [decision 0001](decisions/0001-encrypted-only.md)).

## What encryption doesn't hide (metadata)

- Your pubkey, the `d` tag (e.g. `usus/backup`: it shows which apps you use), the date and approximate size of each item, and which relays hold it.
- The public part of NIP-51 lists.

## Risks to keep in mind

1. **The nsec is the only key and there's no forward secrecy.** If it ever leaks, every old backup still on some relay can be read. In the very long term, Nostr's elliptic-curve cryptography doesn't withstand quantum computers.
2. **Deleting isn't final.** NIP-09 is a request; some relay may keep (encrypted) copies.
3. **Your signer sees the decrypted content.** With Amber it stays on your phone; with a remote bunker you trust whoever hosts it.
4. **Bunker connection.** Tansu stores that connection's key (not your nsec). With it the signer can be asked to sign or decrypt: check the permissions in Amber or your bunker and revoke the connection if you lose the device.
5. **Web:** the app is served from GitHub Pages; if someone compromised the repository, they could serve code that asks your signer to decrypt. The Android app is signed (fingerprint in About).

## Recommendations

- An account **only for backups**, separate from your social one, with the nsec only in Amber.
- **A few trusted relays** (your own or a paid one) instead of the default public ones.
- **Don't upload** passwords, ID documents, bank or health data, or anything that shouldn't be readable if the nsec leaks years from now. If needed, add your own password-based encryption first.
