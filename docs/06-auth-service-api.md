# Auth Service API (estado actual)

Base URL por defecto: `http://localhost:8080`

## Endpoints
- `GET /health`
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/password/issue`

## Ejemplo register
```bash
curl -sS -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName":"Ana",
    "lastName":"Lopez",
    "age":"28",
    "email":"ana@example.com",
    "phone":"+5215512345678",
    "password":"Secret123"
  }'
```

## Ejemplo login
```bash
curl -sS -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"ana@example.com","password":"Secret123"}'
```

## Notas
- Persistencia actual: memoria (adaptador temporal).
- Cola asíncrona para publicación MQTT: usa `mosquitto_pub` del sistema.
- Envío de clave temporal por correo: implementación SMTP por socket (sin frameworks).
