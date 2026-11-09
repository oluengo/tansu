# Changelog

## 0.3.1 (beta)

- Web: Amber se conecta como firmante remoto (NIP-46, enlace `nostrconnect://`) en vez de NIP-55 por enlace. Antes, el texto descifrado volvía en la dirección de la página y podía quedar en el historial del navegador (y sincronizarse). Ahora lo descifrado solo vive en memoria. Las cuentas de Amber añadidas en la web hay que volver a añadirlas; al abrir se borran los restos de la versión anterior.
- Documentación: sección de seguridad (qué protege el cifrado y qué no).

## 0.3.0 (beta)

- Contenido con dos vistas: **Visual** (fichas, árboles de carpetas, tablas, etiquetas, fechas legibles, sí/no; genérica para cualquier app) y **Código** (el JSON tal cual, que es lo que se edita). Visual por defecto; se recuerda la última elegida. Android y web.

## 0.2.0 (beta)

- Editar listas NIP-51: añadir y editar elementos privados (cifrados con NIP-44), quitar públicos o hacerlos privados. Nunca se añade nada público; la versión anterior queda en la papelera.
- Versión web con PWA opcional: Amber (Android), extensión NIP-07, bunker o npub; las mismas funciones que la app.
- Firma propia de Tansu (huella en Ajustes → Acerca de y en el README).

## 0.1.0 (beta)

- Primera versión: varias cuentas (Amber, bunker, npub), explorador de datos de apps (NIP-78) por carpetas, copias por partes de Usus, descifrar, editar, exportar, copiar a otra cuenta, eliminar y papelera local.
- Listas NIP-51 en solo lectura (también la parte privada).
- Ajustes: cuentas, apariencia e idioma (ca/es/en), relays, papelera y Acerca de con créditos.
