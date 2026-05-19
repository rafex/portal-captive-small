# Despliegue en Raspberry Pi 3B

## Requisitos del host
- Raspberry Pi OS Lite (64-bit recomendado)
- Acceso root/sudo
- Conectividad a internet

## 1) Preparar host para LXC (limpiar Docker/Podman)
Script:
- `scripts/install/rpi3b-prepare-lxc.sh`

Ejecución:
```bash
sudo bash scripts/install/rpi3b-prepare-lxc.sh
```

Opcional backup de imágenes antes de borrar:
```bash
sudo KEEP_IMAGES_BACKUP=true bash scripts/install/rpi3b-prepare-lxc.sh
```

Qué hace:
- detiene y deshabilita servicios Docker/Podman/containerd
- elimina contenedores, volúmenes, networks e imágenes
- purge de paquetes Docker/Podman
- elimina rutas de datos residuales
- instala dependencias base de LXC y utilidades

## 2) Instalación desde release
Script:
- `scripts/install/rpi3b-lxc-install.sh <version-tag>`

Ejemplo:
```bash
sudo bash scripts/install/rpi3b-lxc-install.sh v0.1.0
```

## 3) Instalación directa y arranque en LXC
Script:
- `scripts/install/rpi3b-direct-install.sh <version-tag>`

Ejemplo:
```bash
sudo bash scripts/install/rpi3b-direct-install.sh v0.1.0
```

El script:
- crea/arranca contenedor LXC
- instala runtime dentro del contenedor
- despliega artefactos en `/opt/portal-captive-small`
- compila worker Rust y backend Java dentro del contenedor
- levanta `mosquitto`, `db-mqtt-worker` y `auth-service`
- valida endpoint `/health`

## 4) Verificación
Dentro del contenedor:
```bash
curl -sS http://127.0.0.1:8080/health
curl -sS http://127.0.0.1:8080/health/db-mqtt
curl -sS http://127.0.0.1:8080/metrics/db-mqtt
curl -sS http://127.0.0.1:8080/metrics/db-mqtt/prometheus
```
