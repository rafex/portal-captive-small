# Configuración TOML

Archivo: `config/portal-config.toml`

Contiene:
- TTL de sesión de internet.
- Campos habilitados para registro/login.
- Configuración MQTT.
- Configuración SMTP para envío de clave autogenerada.
- Configuración SSH/UCI para OpenWrt.

## Modelo i18n para registro

El archivo también incluye un modelo internacionalizable separado en 3 capas:

- Definición técnica de campos: `[fields.*]`
- Traducciones por idioma: `[i18n.<lang>.fields.*]` y `[i18n.<lang>.messages]`
- Plantillas funcionales: `[templates.*]`

Secciones clave:

- `[meta]`: `default_lang`, `supported_langs`, `version`
- `[branding]`: logo, fondo y color primario del portal
- `[fields.*]`: tipo, restricciones y metadatos técnicos
- `[i18n.es_MX|en_US|fr_FR.*]`: etiquetas/placeholder/textos
- `[templates.hotel|restaurant|school]`: combinación de campos y obligatoriedad

Compatibilidad:

- La sección legacy `[registration]` se conserva para no romper componentes existentes.
- El backend puede migrar de forma gradual a `[templates.*]` e `[i18n.*]`.
