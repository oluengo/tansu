# Seguridad y privacidad en Tansu

*Qué protege el cifrado, qué no, y qué conviene no subir.*

## Lo que está protegido

- **Contenido cifrado de verdad.** Lo que Tansu publica va cifrado con NIP-44 (versión 2) para la propia cuenta. NIP-44 fue auditado por Cure53 en diciembre de 2023 (<https://cure53.de/audit-report_nip44-implementations.pdf>). Los relays guardan el texto cifrado, no pueden leerlo.
- **Tansu no tiene tu clave.** Firma, cifra y descifra tu firmante (Amber, bunker o extensión). Tansu nunca ve ni guarda la nsec.
- **Lo descifrado solo en memoria.** Ni la app ni la web lo guardan en disco. La papelera guarda los eventos tal cual (cifrados). Solo «Exportar archivo» crea un archivo descifrado, porque lo pides tú.
- **Nunca se publica nada legible** (ver [decisión 0001](decisiones/0001-solo-cifrado.md)).

## Lo que el cifrado no oculta (metadatos)

- Tu pubkey, la etiqueta `d` (p. ej. `usus/backup`: se ve qué apps usas), la fecha y el tamaño aproximado de cada dato, y en qué relays está.
- La parte pública de las listas NIP-51.

## Riesgos a tener en cuenta

1. **La nsec es la única llave y no hay «secreto hacia adelante».** Si algún día se filtra, se pueden leer todas las copias antiguas que sigan en algún relay. A muy largo plazo, la criptografía de curva elíptica de Nostr no resiste ordenadores cuánticos.
2. **Borrar no es definitivo.** NIP-09 es una petición; algún relay puede conservar copias (cifradas).
3. **Tu firmante ve el contenido descifrado.** Con Amber se queda en tu móvil; con un bunker remoto confías en quien lo aloja.
4. **Conexión con un bunker.** Tansu guarda la clave de esa conexión (no tu nsec). Con ella se puede pedir al firmante que firme o descifre: revisa los permisos en Amber o en tu bunker y revoca la conexión si pierdes el dispositivo.
5. **Web:** la app se sirve desde GitHub Pages; si alguien comprometiera el repositorio, podría servir código que pidiera a tu firmante descifrar. La app Android va firmada (huella en Acerca de).

## Recomendaciones

- Una cuenta **solo para copias**, distinta de la social, con la nsec únicamente en Amber.
- **Pocos relays de confianza** (uno propio o de pago) en vez de los públicos de serie.
- **No subas** contraseñas, documentos de identidad, datos bancarios o de salud, ni nada que no deba poder leerse si dentro de años se filtra la nsec. Si hace falta, añade antes un cifrado con contraseña propio.
