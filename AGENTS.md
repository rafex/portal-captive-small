# AGENTS.md

## Objetivo
Este repositorio implementa un portal cautivo ultraligero para Raspberry Pi 3B con aislamiento LXC (sin LXD), frontend web liviano, backend Java 21 (hexagonal, microservicios, GraalVM) y un conector SQLite en Rust (`.so`) para reducir fricción de dependencias nativas en compilación nativa.

## Convenciones operativas
- `just` es el orquestador principal.
- `make` ejecuta tareas atómicas de construcción.
- `just` puede invocar `make`; `make` no invoca `just`.
- No duplicar comandos entre `Justfile` y `Makefile`; `Justfile` compone, `Makefile` implementa.

## Estructura clave
- `frontend/javascripts/portal`: Vite + HTML + CSS + JS puro.
- `backend/java/portal`: parent Maven + microservicios hexagonales.
- `backend/rust/database-conector-sqlite`: librería compartida `.so`.
- `sql/`: scripts de esquema e inicialización por microservicio.
- `containers/`: configuración LXC y recursos de despliegue.
- `scripts/`: automatización por dominios semánticos.

## Flujo de trabajo recomendado
1. `just validate`
2. `just build`
3. `just package`
4. `just release-local`

## Requisitos técnicos
- Java 21
- Maven 3.9+
- GraalVM 21+
- Node.js 20+
- npm 10+
- Rust stable
- SQLite3
- LXC (`lxc-*` CLI)
- OpenSSH client

## Alcance funcional esperado
- Registro por plantillas: hotel, restaurante, escuela, casa, personalizado.
- Login con correo o teléfono + contraseña.
- Generación de clave temporal por correo (implementación base lista para integrar proveedor SMTP).
- Integración asíncrona con MQTT (Mosquitto).
- Integración con OpenWrt vía SSH + UCI para habilitar navegación.
