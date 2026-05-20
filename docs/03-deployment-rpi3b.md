# Despliegue en Raspberry Pi 3B

## Requisitos del host
- Raspberry Pi OS Lite (64-bit recomendado)
- Acceso root/sudo
- Conectividad a internet

## Build de release
El release se construye en GitHub Actions con:
- matrix de runners nativos GitHub (`x86_64` y `arm64`) sin cross-compilación
- binario nativo GraalVM 25 del `auth-service` por arquitectura (`auth-service-<arch>`)
- binario Rust `db-mqtt-worker` por arquitectura (`db-mqtt-worker-<arch>`)

No se requiere `openjdk-21-jre-headless` en runtime del contenedor.

## 1) Preparar host para LXC
```bash
sudo bash scripts/install/rpi3b-prepare-lxc.sh
```

## 2) Instalar release en host
```bash
sudo bash scripts/install/rpi3b-lxc-install.sh v0.1.0
```

## 3) Arrancar stack en LXC
```bash
sudo bash scripts/install/rpi3b-direct-install.sh v0.1.0
```

## 4) Smoke de artifacts release
```bash
sudo VERSION=v0.1.0 bash scripts/valid/rpi3b-lxc-smoke.sh
```

## 5) Verificación
```bash
curl -sS http://127.0.0.1:8080/health
curl -sS http://127.0.0.1:8080/health/db-mqtt
curl -sS http://127.0.0.1:8080/metrics/db-mqtt
curl -sS http://127.0.0.1:8080/metrics/db-mqtt/prometheus
```
