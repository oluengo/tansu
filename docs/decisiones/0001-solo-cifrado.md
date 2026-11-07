# 0001 · Tansu solo publica datos cifrados

*Estado: aceptada · 7 nov 2026*

## Decisión

Tansu es para gestionar copias de seguridad y datos privados. **Todo lo que publica va cifrado con NIP-44 para la propia cuenta.**

- Editar solo se permite en datos ya cifrados con NIP-44. Lo que está sin cifrar se ve (con aviso) pero no se toca.
- Antes de publicar se comprueba que el firmante devolvió texto NIP-44; si no, no se publica nada.
- Borrar es una petición NIP-09 (kind 5), que no tiene contenido.
- Listas NIP-51 (desde 0.2): lo que se añade o edita va siempre a la parte privada, cifrada con NIP-44. Los elementos públicos existentes solo se pueden quitar o pasar a privados; si una lista guardada añadiera algo público, no se publica. Si la lista tenía parte privada, hay que descifrarla antes de editar para no perderla.

## Motivo

Un error al guardar no debe dejar a la vista una copia de seguridad. Nadie más necesita leer estos datos.

## Qué la haría cambiar

Si hiciera falta publicar una lista pública a propósito (por ejemplo, una carpeta de marcadores compartida), sería una acción aparte y explícita.
