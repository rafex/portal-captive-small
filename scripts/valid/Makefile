.PHONY: run mqtt-e2e backend-http-smoke backend-selftest db-mqtt-e2e

run: backend-selftest backend-http-smoke
	@echo "scripts/valid checks"
	@if command -v mosquitto_pub >/dev/null 2>&1 && command -v mosquitto_sub >/dev/null 2>&1; then \
		echo "Running MQTT auth E2E"; \
		bash mqtt-e2e.sh; \
	else \
		echo "Skipping mqtt-e2e.sh (mosquitto_pub/sub not installed)"; \
	fi
	@if command -v mosquitto >/dev/null 2>&1 && command -v mosquitto_pub >/dev/null 2>&1 && command -v mosquitto_sub >/dev/null 2>&1; then \
		echo "Running DB MQTT E2E"; \
		bash db-mqtt-local-stack.sh; \
	else \
		echo "Skipping db-mqtt-local-stack.sh (mosquitto + clients not installed)"; \
	fi

backend-selftest:
	cd ../../backend/java/portal && mvn -q -DskipTests test-compile
	cd ../../backend/java/portal && java -ea -cp services/auth-service/target/test-classes:services/auth-service/target/classes com.portal.auth.AuthServiceSelfTest

backend-http-smoke:
	cd ../.. && bash scripts/valid/backend-http-smoke.sh

mqtt-e2e:
	bash mqtt-e2e.sh

db-mqtt-e2e:
	bash db-mqtt-local-stack.sh
