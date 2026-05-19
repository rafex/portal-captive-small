# Raspberry Pi 3B + LXC Smoke

## Objetivo
Validar despliegue integral del stack con arquitectura:
`frontend -> MQTT -> backend/java -> MQTT -> rust-worker -> SQLite(WAL)`

en un entorno compatible con Raspberry Pi 3B y LXC puro.

## Requisitos host
- LXC (`lxc-create`, `lxc-start`, `lxc-attach`, `lxc-stop`)
- `rsync`, `curl`, `sqlite3`
- acceso root/sudo

## Instalación release
Script recomendado:
- `scripts/install/rpi3b-lxc-install.sh <version-tag>`

## Smoke local
Script:
- `scripts/valid/rpi3b-lxc-smoke.sh`

Valida:
- creación/arranque del contenedor
- instalación de runtime base
- build rust/java en contenedor
- arranque `mosquitto`, `db-mqtt-worker`, `auth-service`
- endpoints:
  - `/health`
  - `/health/db-mqtt`

## Nota
El smoke local asume disponibilidad de toolchain en contenedor para build. En flujo release real, debe consumirse `tar.gz` publicado y evitar compilación en sitio.
