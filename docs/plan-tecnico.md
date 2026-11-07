# Tansu · Plan técnico v0.1

*7 nov 2026*

Explorador tipo gestor de archivos para los datos privados que las apps de Nostr guardan en tu cuenta. Android, multicuenta, todo cifrado.

## Decisiones

| Tema | Decisión |
|---|---|
| Nombre | Tansu (cómoda japonesa de cajones). Sin caracteres japoneses en la marca |
| Icono | Carpeta índigo con el Purple Nostrich de SovrynMatt (créditos en Acerca de y CREDITS.md) |
| Privacidad | Solo publica datos cifrados con NIP-44 para la propia cuenta (ADR 0001) |
| Cuentas | Varias: Amber, bunker, npub (ADR 0002) |
| Plataforma | Android nativo: Kotlin, Compose, Material 3. minSdk 26 |
| Base | Cliente Nostr, cripto y estilos de Wavestr |
| Idiomas | ca/es/en, por defecto el del dispositivo o inglés |
| Distribución | GitHub Releases + Obtainium + Zapstore. Rama `beta` → «Tansu Beta» |

## Qué se lee

- **Datos de apps (NIP-78, kind 30078)** del autor. Se agrupan por el prefijo de `d` (`wavestr/…`, `usus/…`). Si existen `d/0…d/N` además de `d`, es una copia por partes (formato de Usus: índice JSON + gzip en base64 repartido en partes de 40 000 caracteres).
- **Listas NIP-51** (10000–10063 y 30000–30030), con su parte privada (NIP-44 o NIP-04 antiguo).
- Relays: la lista NIP-65 de cada cuenta más los de Tansu.

## Operaciones

| Operación | Cómo |
|---|---|
| Descifrar | Firmante de la cuenta (Amber en silencio si dio permiso, si no abre su pantalla; o bunker) |
| Editar | Solo NIP-44. JSON validado y compactado. Copias por partes: se rehacen índice y partes; las sobrantes se borran |
| Exportar | Archivo JSON/texto con el contenido descifrado (una copia de Usus se puede importar en Usus) |
| Copiar a otra cuenta | Se vuelve a cifrar para la cuenta de destino |
| Eliminar | NIP-09 (kind 5) con `e` y `a` de todos los eventos; antes, copia en la papelera |
| Restaurar | Republica los eventos cifrados con fecha nueva |
| Editar listas NIP-51 | Privados: añadir, editar, quitar (cifrados con NIP-44). Públicos: quitar o hacer privados. Nunca se añade nada público |

## Fases

- **0.1 (beta):** todo lo anterior; listas en solo lectura.
- **0.2 (beta):** editar listas NIP-51 sin hacer nada público. Firma propia de Tansu.
- **Siguiente:** crear listas privadas nuevas, importar un archivo como nueva entrada, comparar versiones, aviso de copias antiguas.
