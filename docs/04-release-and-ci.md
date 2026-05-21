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
- imagen LXC base (arm64):
  - `lxc-image-<version>-arm64.tar.gz` + `.sha256`
- `fat-jar` por arquitectura:
  - `auth-service-fat-<version>-x86_64.jar` + `.sha256`
  - `auth-service-fat-<version>-arm64.jar` + `.sha256`
- binario Rust por arquitectura:
  - `db-mqtt-worker-<version>-x86_64` + `.sha256`
  - `db-mqtt-worker-<version>-arm64` + `.sha256`
- `script-install.sh`

## Trigger
- Push de tags `v*`: build + empaquetado + publicación de release.
- Push/PR a `main` y `develop`: validaciones rápidas/calidad (sin release, sin OWASP).
- `security-scan`: OWASP Dependency-Check en workflow separado (manual y programado diario).

## Seguridad (OWASP)
- `ci-quality` no ejecuta OWASP para evitar latencia y fallos por actualización de base en cada push.
- OWASP se ejecuta en `security-scan` con:
  - cache de DB (`.cache/dependency-check`)
  - fallback de `NVD_API_KEY` (si existe, se usa; si no, continúa sin API key)
  - reporte HTML como artifact.
- `quality-local`/hooks locales tampoco ejecutan OWASP por defecto.

## Reglas de tags
- Antes de push de tag, el hook `pre-push` valida:
  - existencia de `CHANGE.md` y `RELEASE.md`
  - presencia del tag (`vX.Y.Z`) en ambos archivos.

## Instalación por curl
Soporte para ejecución estilo:
```bash
curl -fsSL <url>/script-install.sh | bash
```
