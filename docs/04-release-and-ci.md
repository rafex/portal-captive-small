# CI/CD y releases

## Objetivo
Generar artefactos reproducibles frontend/backend y checksums SHA256 en GitHub Actions.

## Artefactos
- frontend para instalación:
  - `frontend-<version>.tar.gz` + `.sha256`
- frontend build productivo (Vite):
  - `frontend-id-build-<version>.tar.gz` + `.sha256`
  - `frontend-id-build-<version>.zip` + `.sha256`
- backend por arquitectura:
  - `backend-<version>-x86_64.tar.gz` + `.sha256`
  - `backend-<version>-arm64.tar.gz` + `.sha256`
- `fat-jar` por arquitectura:
  - `auth-service-fat-<version>-x86_64.jar` + `.sha256`
  - `auth-service-fat-<version>-arm64.jar` + `.sha256`
- binario Rust por arquitectura:
  - `db-mqtt-worker-<version>-x86_64` + `.sha256`
  - `db-mqtt-worker-<version>-arm64` + `.sha256`
- `script-install.sh`

## Trigger
- Push de tags `v*`: build + empaquetado + publicación de release.
- Push/PR a `main` y `develop`: solo validaciones/calidad (sin release).

## Instalación por curl
Soporte para ejecución estilo:
```bash
curl -fsSL <url>/script-install.sh | bash
```
