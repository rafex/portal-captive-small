COMPOSE_FILE := docker/compose.yaml

.PHONY: lint docker-build docker-up docker-down docker-logs docker-ps docker-clean

lint:
	@echo "Validando configuración de contenedores LXC"
	@test -f lxc/portal-captive.conf

# ── Docker / Podman ──────────────────────────────────────────────────────────

docker-build:
	bash ../scripts/docker/build.sh

docker-build-no-cache:
	bash ../scripts/docker/build.sh --no-cache

docker-up:
	bash ../scripts/docker/stack.sh up

docker-up-build:
	bash ../scripts/docker/stack.sh up --build

docker-down:
	bash ../scripts/docker/stack.sh down

docker-restart:
	bash ../scripts/docker/stack.sh restart

docker-logs:
	bash ../scripts/docker/stack.sh logs

docker-ps:
	bash ../scripts/docker/stack.sh ps

docker-clean:
	bash ../scripts/docker/stack.sh clean

# Servicios disponibles en el stack:
#   Portal (frontend + API): http://localhost:8080
#   MQTT broker:             localhost:1883
