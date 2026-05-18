# PLAN.md

## SpecNative
### Meta
- `spec_id`: `portal-captive-small-mqtt-rust-sqlite-wal`
- `version`: `1.2.0`
- `status`: `in_progress`
- `date`: `2026-05-18`

### Context
Se requiere alta concurrencia y desacoplamiento del acceso a datos para el portal cautivo. La arquitectura objetivo define un flujo asíncrono con MQTT y un worker Rust dedicado a SQLite en modo WAL.

### Objective
Implementar y estabilizar el flujo:
`frontend -> MQTT -> backend/java (negocio) -> MQTT -> rust-sqlite-worker -> SQLite (WAL)`

### Scope
- Backend Java publica/consume comandos de negocio y usa repositorio de usuarios por RPC MQTT.
- Worker Rust procesa comandos de datos y opera SQLite con WAL.
- Contratos base para `user_save`, `user_find_email`, `user_find_phone`.
- Configuración por TOML para tópicos, timeouts y modo de repositorio.

### NonGoals
- Migrar todos los microservicios a worker Rust en esta fase.
- Implementar particionado multi-broker MQTT.
- Implementar cluster SQLite (fuera de alcance de SQLite embebido).

### Architecture
- Entrada negocio: HTTP + MQTT en Java.
- Persistencia: `UserRepository` con estrategia `mqtt_rust`.
- Data-plane: `db-mqtt-worker` (Rust) suscrito a `portal/db/user/request`.
- Respuesta RPC: `replyTopic` por `requestId` (`portal/db/user/response/<requestId>`).

### Data Contracts
- Request base:
  - `requestId`: string
  - `op`: `user_save|user_find_email|user_find_phone`
  - `replyTopic`: string
  - `payload` plano por operación
- Response base:
  - `requestId`: string
  - `status`: `ok|error`
  - `error`: string opcional
  - `found`: bool opcional
  - datos de usuario cuando aplique

### SQLite WAL Policy
- `PRAGMA journal_mode=WAL;`
- `PRAGMA synchronous=NORMAL;`
- `PRAGMA busy_timeout=5000;`
- `PRAGMA foreign_keys=ON;`

### Milestones
1. `M1` Repositorio Java `mqtt_rust` funcional con `replyTopic` por solicitud.
2. `M2` Worker Rust MQTT + SQLite WAL con operaciones CRUD base de usuario.
3. `M3` Pruebas E2E MQTT request/response en entorno con mosquitto.
4. `M4` Hardening: retries idempotentes, métricas y timeouts por operación (parcial).

### Acceptance Criteria
- Registro y login funcionan con `repository.type = "mqtt_rust"`.
- Worker Rust persiste y consulta usuarios en SQLite WAL.
- Errores de data-plane se reflejan como respuesta `status=error` con `requestId`.
- Tests locales (`make test`) siguen verdes sin requerir broker para smoke base.

### Risks
- Contención en `mosquitto_pub/sub` por invocación por proceso en Java.
- Parser JSON simple en Java puede fallar con payloads complejos.
- Timeouts agresivos pueden degradar UX bajo carga alta.

### Mitigations
- Evolucionar a cliente MQTT embebido en Java (fase siguiente).
- Reemplazar parser JSON/TOML por parser robusto interno.
- Añadir métricas de latencia y reintentos con backoff.

### Current Progress
- `M1` implementado.
- `M2` implementado.
- `M3` implementado a nivel scripts/CI (`db-mqtt-e2e`, `auth-mqtt-rust-e2e`) y pendiente de ejecución en entorno con mosquitto activo.
- `M4` parcial: retries/backoff y logging de latencia por intento implementados; falta métricas centralizadas/exportables.

### Execution Notes
- En CI se instala `mosquitto` + `mosquitto-clients` para habilitar E2E MQTT.
- En local sin mosquitto, los tests E2E MQTT se saltan de forma explícita para no bloquear desarrollo.
