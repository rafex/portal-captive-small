# portal-captive-small

Portal cautivo ultraligero para Raspberry Pi 3B, orientado a despliegue reproducible, microservicios y aislamiento con LXC puro (sin LXD).

## Stack objetivo
- Frontend: Vite + HTML + CSS + JavaScript puro
- Backend: Java 21 (solo bibliotecas estándar JDK, sin frameworks), Maven, arquitectura hexagonal
- Persistencia: SQLite por microservicio
- Módulo nativo: Rust `.so` + worker MQTT para acceso SQLite WAL
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
just test
just build
just package
```

## Raspberry Pi 3B (directo)
1. Preparar host para LXC (borra Docker/Podman):
```bash
sudo bash scripts/install/rpi3b-prepare-lxc.sh
```

2. Instalar y arrancar stack en LXC:
```bash
sudo bash scripts/install/rpi3b-direct-install.sh v0.1.0
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
