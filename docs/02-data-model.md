# Modelo de datos

## Principios
- Base de datos por microservicio.
- IDs estables (UUID texto).
- Campos de auditoría (`created_at`, `updated_at`).
- Índices para login y deduplicación.

## Campos de registro
- nombres
- apellidos
- edad
- correo electrónico
- teléfono
- celular
- dirección (con metadatos de OpenStreetMap)
- redes sociales: facebook, instagram, tiktok, x
- contraseña (hash + salt)

## Login
- Identificador: correo o teléfono.
- Credencial: contraseña.

## Plantillas de registro
- hotel
- restaurante
- escuela
- casa
- personalizado
