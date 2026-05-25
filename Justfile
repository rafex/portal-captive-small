set shell := ["bash", "-cu"]

version := "0.1.0"
repo_root := justfile_directory()

validate:
    make validate

test:
    make test

build:
    make build

package:
    make package VERSION={{version}}

release-local:
    make release-local VERSION={{version}}

install-githooks:
    bash scripts/git/install-githooks.sh

uninstall-githooks:
    bash scripts/git/uninstall-githooks.sh

pre-commit-lint:
    bash scripts/git/pre-commit-lint.sh

config-ufw-lxc ext_if="eth0" ct_ip="10.0.3.15" ct_net="10.0.3.0/24" lxc_if="lxcbr0":
    sudo EXT_IF={{ext_if}} CT_IP={{ct_ip}} CT_NET={{ct_net}} LXC_IF={{lxc_if}} bash scripts/config/rpi3b-ufw-lxc-network.sh

config-ufw-lxc-auto lxc_name="portal-captive" ct_net="10.0.3.0/24" lxc_if="lxcbr0":
    @set -euo pipefail; \
    EXT_IF="$$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($$i=="dev") {print $$(i+1); exit}}')"; \
    CT_IP="$$(lxc-info -n {{lxc_name}} -iH | head -n1)"; \
    if [[ -z "$$EXT_IF" || -z "$$CT_IP" ]]; then \
      echo "No se pudo detectar EXT_IF o CT_IP (contenedor={{lxc_name}})"; \
      exit 1; \
    fi; \
    echo "[just config-ufw-lxc-auto] EXT_IF=$$EXT_IF CT_IP=$$CT_IP CT_NET={{ct_net}} LXC_IF={{lxc_if}}"; \
    sudo EXT_IF="$$EXT_IF" CT_IP="$$CT_IP" CT_NET="{{ct_net}}" LXC_IF="{{lxc_if}}" bash scripts/config/rpi3b-ufw-lxc-network.sh

rpi3b-direct-install version="":
    @set -euo pipefail; \
    VER="{{version}}"; \
    if [[ -z "$$VER" ]]; then \
      VER="$$(git ls-remote --tags --refs origin 'v*' | awk -F/ '{print $$3}' | sort -V | tail -n1)"; \
    fi; \
    if [[ -z "$$VER" ]]; then \
      echo "No se pudo detectar tag release. Usa: just rpi3b-direct-install version=vX.Y.Z"; \
      exit 1; \
    fi; \
    echo "[just rpi3b-direct-install] VERSION=$$VER"; \
    sudo bash scripts/install/rpi3b-direct-install.sh "$$VER"

tag-create tag:
    bash scripts/git/tag-create.sh {{tag}}
    if ! git diff --quiet -- CHANGE.md RELEASE.md; then git add CHANGE.md RELEASE.md; git commit -m "docs(release): prepare {{tag}}"; fi
    git tag -a {{tag}} -m "Release {{tag}}"

tag-push tag:
    git push origin {{tag}}

tag-repush tag:
    git push origin :refs/tags/{{tag}} || true
    git push origin {{tag}}

# ── Docker / Podman (stack local macOS) ──────────────────────────────────────
# El frontend está embebido en auth-service (src/main/resources/portal/).
# Un solo puerto expone portal + API: http://localhost:8080

# Compila código fuente (Java + Rust) y construye las imágenes de contenedor
docker-build:
    bash scripts/docker/build.sh

# Construye imágenes ignorando la caché
docker-build-no-cache:
    bash scripts/docker/build.sh --no-cache

# Levanta el stack completo en segundo plano
docker-up:
    bash scripts/docker/stack.sh up

# Levanta el stack reconstruyendo imágenes
docker-up-build:
    bash scripts/docker/stack.sh up --build

# Fuerza rebuild sin caché y recrea contenedores
docker-up-rebuild:
    podman pull docker.io/library/eclipse-mosquitto:2.0 && podman tag docker.io/library/eclipse-mosquitto:2.0 eclipse-mosquitto:2.0
    podman pull docker.io/library/maven:3.9.9-eclipse-temurin-21 && podman tag docker.io/library/maven:3.9.9-eclipse-temurin-21 maven:3.9.9-eclipse-temurin-21
    podman pull docker.io/library/rust:1-slim-bookworm && podman tag docker.io/library/rust:1-slim-bookworm rust:1-slim-bookworm
    podman pull docker.io/library/debian:12-slim && podman tag docker.io/library/debian:12-slim debian:12-slim
    podman pull docker.io/library/eclipse-temurin:25-jre-alpine && podman tag docker.io/library/eclipse-temurin:25-jre-alpine eclipse-temurin:25-jre-alpine
    podman build --no-cache -t portal-captive/mosquitto:local -f containers/docker/Dockerfile.mosquitto .
    podman build --no-cache -t portal-captive/db-mqtt-worker:local -f containers/docker/Dockerfile.rust-sqlite .
    podman build --no-cache -t portal-captive/auth-service:local -f containers/docker/Dockerfile.auth-service .
    podman compose -f containers/docker/compose.yaml down --remove-orphans || true
    podman compose -f containers/docker/compose.yaml up -d --no-build --force-recreate

# Alias corto: levanta stack reconstruyendo imágenes en detached
docker-up-build-detached:
    bash scripts/docker/stack.sh up --build

# Para y elimina los contenedores
docker-down:
    bash scripts/docker/stack.sh down

# Alias corto: baja todo lo levantado por docker/podman compose
down:
    bash scripts/docker/stack.sh down

# Reinicia todos los servicios
docker-restart:
    bash scripts/docker/stack.sh restart

# Muestra logs en tiempo real (servicio opcional: mosquitto|db-mqtt-worker|auth-service|frontend)
docker-logs svc="":
    @if [[ -n "{{svc}}" ]]; then \
      bash scripts/docker/stack.sh logs {{svc}}; \
    else \
      bash scripts/docker/stack.sh logs; \
    fi

# Lista los contenedores del stack
docker-ps:
    bash scripts/docker/stack.sh ps

# Para el stack y elimina volúmenes de datos (destructivo)
docker-clean:
    bash scripts/docker/stack.sh clean

# ── Admin portal/CLI ──────────────────────────────────────────────────────────

# Crea o actualiza usuario admin/viewer en SQLite local
admin-user-create username role="viewer" password db_path="data/auth-service.db":
    DB_PATH={{db_path}} bash scripts/runtime/admin-user-create.sh {{username}} {{role}} {{password}}
