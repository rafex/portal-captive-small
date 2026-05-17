# Despliegue en Raspberry Pi 3B

## Requisitos del host
- Raspberry Pi OS Lite (64-bit recomendado)
- LXC instalado (`apt install lxc`)
- OpenSSH client
- Mosquitto

## Flujo de instalación
1. Descargar artefactos de release.
2. Verificar SHA256.
3. Ejecutar `script-install.sh`.
4. Crear/arrancar contenedor LXC de portal.
5. Cargar configuración `config/portal-config.toml`.

## LXC sin LXD
Archivos de ejemplo en `containers/lxc`.
