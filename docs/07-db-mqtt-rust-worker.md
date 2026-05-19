# DB MQTT Rust Worker

## Objetivo
Desacoplar persistencia de negocio Java mediante RPC sobre MQTT hacia un worker Rust con SQLite en WAL.

## Topología
- Request topic: `portal/db/user/request`
- Reply topic dinámico: `portal/db/user/response/<requestId>`

## Operaciones soportadas
- `user_save`
- `user_find_email`
- `user_find_phone`

## WAL y concurrencia
El worker configura al iniciar:
- `PRAGMA journal_mode=WAL;`
- `PRAGMA synchronous=NORMAL;`
- `PRAGMA busy_timeout=5000;`
- `PRAGMA foreign_keys=ON;`

## Endpoints operativos
- `GET /health/db-mqtt`
- `GET /metrics/db-mqtt`
- `GET /metrics/db-mqtt/prometheus`

## Ejecución local
```bash
bash scripts/valid/db-mqtt-local-stack.sh
```

## Variables del worker
- `MQTT_HOST`
- `MQTT_PORT`
- `DB_USER_REQUEST_TOPIC`
- `SQLITE_DB_PATH`
