.PHONY: run mqtt-e2e

run:
	@echo "scripts/valid checks"
	@if command -v mosquitto_pub >/dev/null 2>&1 && command -v mosquitto_sub >/dev/null 2>&1; then \
		echo "Running MQTT E2E"; \
		bash mqtt-e2e.sh; \
	else \
		echo "Skipping mqtt-e2e.sh (mosquitto_pub/sub not installed)"; \
	fi

mqtt-e2e:
	bash mqtt-e2e.sh
