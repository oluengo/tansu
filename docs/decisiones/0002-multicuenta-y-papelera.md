# 0002 · Varias cuentas y papelera local

*Estado: aceptada · 7 nov 2026*

## Decisión

- Se pueden añadir varias cuentas (Amber, bunker o npub en solo lectura) y cambiar entre ellas. Se guarda la pubkey y cómo firmar; la conexión con cada bunker va cifrada con el Android Keystore. Nunca se guarda una nsec.
- «Copiar a otra cuenta» descifra con la cuenta de origen y vuelve a cifrar para la de destino, con la misma etiqueta `d`.
- Antes de borrar o sobrescribir, los eventos originales (cifrados) se guardan en una papelera local. Restaurar los vuelve a publicar con fecha nueva: los relays rechazan la versión antigua tras un borrado, pero el contenido sigue siendo válido porque está cifrado para la misma cuenta.

## Límites

- NIP-09 es una petición: algún relay puede quedarse con una copia.
- La papelera no sale del móvil (sin copia de seguridad de Android).
