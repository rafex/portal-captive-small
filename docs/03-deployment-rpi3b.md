# Despliegue en Raspberry Pi 3B

## Requisitos del host
- Raspberry Pi OS Lite (64-bit recomendado)
- Acceso root/sudo
- Conectividad a internet

## Build de release
El release se construye en GitHub Actions con:
- binario nativo GraalVM del `auth-service` para ARMv7 (`auth-service-armv7`)
- binario Rust ARMv7 (`db-mqtt-worker-armv7`)

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
