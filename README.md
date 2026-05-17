# portal-captive-small

Portal cautivo ultraligero para Raspberry Pi 3B, orientado a despliegue reproducible, microservicios y aislamiento con LXC puro (sin LXD).

## Stack objetivo
- Frontend: Vite + HTML + CSS + JavaScript puro
- Backend: Java 21, Maven, arquitectura hexagonal, compilación con GraalVM
- Persistencia: SQLite por microservicio
- Módulo nativo: Rust `.so` para acceso SQLite
- Mensajería asíncrona: MQTT (Mosquitto)
- Integración de red: OpenWrt vía SSH + UCI

## Estructura del repositorio
- `frontend/javascripts/portal`
- `backend/java/portal`
- `backend/rust/database-conector-sqlite`
- `sql`
- `containers`
- `scripts`
- `docs`

## Inicio rápido
```bash
just validate
just build
just package
```

## Build y orquestación
- `Makefile`: tareas atómicas de build y empaquetado.
- `Justfile`: flujo de alto nivel y task manager.

## Artefactos esperados
- `dist/frontend-<version>.tar.gz`
- `dist/frontend-<version>.tar.gz.sha256`
- `dist/backend-<version>.tar.gz`
- `dist/backend-<version>.tar.gz.sha256`
- `dist/script-install.sh`

## Documentación
Ver `docs/` para arquitectura, modelo de datos, despliegue en Raspberry Pi 3B y pipeline de releases.
