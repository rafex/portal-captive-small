.PHONY: run e2e concurrency mqtt-e2e backend-http-smoke backend-selftest db-mqtt-e2e auth-mqtt-rust-e2e auth-mqtt-rust-concurrency rpi3b-lxc-smoke rust-test

run: backend-selftest rust-test backend-http-smoke
	@echo "scripts/valid checks"
	@echo "Quick validate complete (E2E omitted)"

e2e:
	@echo "scripts/valid e2e checks"
	@if command -v mosquitto >/dev/null 2>&1 && command -v mosquitto_pub >/dev/null 2>&1 && command -v mosquitto_sub >/dev/null 2>&1; then \
		echo "Running DB MQTT E2E"; \
		timeout 180s bash db-mqtt-local-stack.sh; \
		echo "Running FULL auth-mqtt-rust E2E"; \
		timeout 180s bash auth-mqtt-rust-e2e.sh; \
	else \
		echo "Skipping db/auth-mqtt-rust e2e (mosquitto + clients not installed)"; \
	fi

concurrency:
	@echo "scripts/valid concurrency checks"
	@if command -v mosquitto >/dev/null 2>&1 && command -v mosquitto_pub >/dev/null 2>&1 && command -v mosquitto_sub >/dev/null 2>&1; then \
		echo "Running auth-mqtt-rust concurrency"; \
		timeout 120s bash auth-mqtt-rust-concurrency.sh; \
	else \
		echo "Skipping concurrency (mosquitto + clients not installed)"; \
	fi

backend-selftest:
	cd ../../backend/java/portal && mvn -q -DskipTests test-compile
	cd ../../backend/java/portal && java -ea -cp services/auth-service/target/test-classes:services/auth-service/target/classes com.portal.auth.AuthServiceSelfTest

rust-test:
	@if command -v cargo >/dev/null 2>&1; then \
		cd ../../backend/rust/database-conector-sqlite && cargo test --quiet; \
	else \
		echo "Skipping rust-test (cargo no instalado)"; \
	fi

backend-http-smoke:
	cd ../.. && bash scripts/valid/backend-http-smoke.sh

mqtt-e2e:
	bash mqtt-e2e.sh

db-mqtt-e2e:
	bash db-mqtt-local-stack.sh

auth-mqtt-rust-e2e:
	bash auth-mqtt-rust-e2e.sh

auth-mqtt-rust-concurrency:
	bash auth-mqtt-rust-concurrency.sh

rpi3b-lxc-smoke:
	bash rpi3b-lxc-smoke.sh
