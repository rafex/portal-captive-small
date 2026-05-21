# SpecNative: Incidente de Arranque LXC en Raspberry Pi 3B

## Meta
- `spec_id`: `rpi3b-lxc-startup-incident`
- `version`: `1.0.0`
- `status`: `closed`
- `date`: `2026-05-21`

## Contexto
Durante la instalación directa en Raspberry Pi 3B (`scripts/install/rpi3b-direct-install.sh`) el despliegue concluía sin dejar operativo el backend HTTP en `:8080`.

## Síntomas observados
- `curl http://127.0.0.1:8080/health` devolvía `connection refused`.
- El contenedor LXC en ocasiones quedaba `STOPPED` tras intentos fallidos.
- En algunos intentos sólo quedaba `mosquitto` activo y no existían procesos `db-mqtt-worker` ni `auth-service`.

## Evidencia técnica relevante
- `pkill -f` en contexto `lxc-attach` cortaba el flujo con `exit=143`.
- Arranque de `auth-service` con ruta incorrecta de configuración (`config/portal-config.toml` en vez de `backend/config/portal-config.toml`).
- `db-mqtt-worker` fallaba por ausencia/permiso de `auth-service.db`.
- `pgrep -f` generaba validaciones ambiguas.
- Arranque con `nohup` dentro de `lxc-attach` no garantizaba persistencia de `db-mqtt-worker`/`auth-service`.

## Análisis de causa raíz
1. Validaciones y limpieza de procesos no robustas dentro de sesiones `lxc-attach`.
2. Dependencia de ruta de configuración no alineada con estructura real del paquete release.
3. Falta de inicialización explícita de SQLite (`data/` + `auth-service.db`) antes del worker.
4. Patrón de daemonización insuficiente para desacoplar procesos del ciclo de vida de `lxc-attach`.

## Reparaciones implementadas
1. Corrección de ruta de configuración a `backend/config/portal-config.toml`.
2. Inicialización explícita de DB y permisos:
   - `mkdir -p /opt/portal-captive-small/data`
   - `touch /opt/portal-captive-small/data/auth-service.db`
   - `chmod 775 data` y `chmod 664 auth-service.db`
3. Eliminación de bloque `pkill -f` en flujo con recreación de contenedor (evita auto-terminación).
4. Sustitución de validación frágil por:
   - PID files (`/run/portal-*.pid`)
   - `health` con reintentos
   - diagnóstico automático con `trap ERR`.
5. Reutilización de `mosquitto` existente para evitar colisión de puerto `1883`.
6. Desacople de `db-mqtt-worker` y `auth-service` usando `setsid -f`.

## Resultado validado
- Instalación directa finaliza correctamente.
- `GET /health` responde `{"status":"ok"}`.
- `GET /health/db-mqtt` responde `{"component":"db_mqtt","healthy":true}`.

## Archivos impactados
- `scripts/install/rpi3b-direct-install.sh`
- `scripts/valid/rpi3b-lxc-smoke.sh`

## Criterios de cierre
- Flujo `rpi3b-direct-install.sh` ejecutable end-to-end sin pasos manuales adicionales.
- Salud HTTP y salud de integración DB/MQTT en estado `ok`.

