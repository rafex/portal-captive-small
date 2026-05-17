# Arquitectura

## Vista general
El sistema se divide en tres bloques:
1. Frontend web liviano para portal cautivo.
2. Backend de microservicios Java con arquitectura hexagonal.
3. Conector nativo Rust para operaciones SQLite críticas.

## Frontend
Ruta: `frontend/javascripts/portal`
- Vite para desarrollo y build.
- HTML/CSS/JS puro para minimizar consumo.
- Módulos UI: login, registro, validación por plantilla.

## Backend Java (hexagonal)
Ruta: `backend/java/portal`
- `portal-parent` como POM padre.
- Puertos de entrada: REST/MQTT consumers.
- Puertos de salida: repositorios SQLite, SMTP, OpenWrt adapter.
- Adaptadores infraestructura: HTTP, MQTT, SQLite bridge (`.so`), SSH/UCI.

## Módulo Rust SQLite
Ruta: `backend/rust/database-conector-sqlite`
- Biblioteca `cdylib` para exponer funciones C ABI/JNI-friendly.
- Objetivo: encapsular acceso SQLite para reducir problemas de dependencia en native-image.

## Asincronía y concurrencia
- Mosquitto como broker ligero.
- Frontend publica eventos de sesión/registro.
- Backend procesa eventos y emite respuestas/eventos de estado.

## Integración OpenWrt
- Adapter SSH ejecuta comandos UCI para altas/bajas de reglas temporales.
- Debe existir timeout y rollback en caso de error.
