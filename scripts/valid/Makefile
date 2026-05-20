.PHONY: run mqtt-e2e backend-http-smoke backend-selftest db-mqtt-e2e auth-mqtt-rust-e2e auth-mqtt-rust-concurrency rpi3b-lxc-smoke frontend-test rust-test

run: backend-selftest frontend-test rust-test backend-http-smoke
	@echo "scripts/valid checks"
	@echo "Skipping mqtt-e2e.sh in default validate (requires external responder stack)"
	@if command -v mosquitto >/dev/null 2>&1 && command -v mosquitto_pub >/dev/null 2>&1 && command -v mosquitto_sub >/dev/null 2>&1; then \
		echo "Running DB MQTT E2E"; \
		bash db-mqtt-local-stack.sh; \
		echo "Running FULL auth-mqtt-rust E2E"; \
		bash auth-mqtt-rust-e2e.sh; \
		echo "Running auth-mqtt-rust concurrency"; \
		bash auth-mqtt-rust-concurrency.sh; \
	else \
		echo "Skipping db/auth-mqtt-rust e2e (mosquitto + clients not installed)"; \
	fi

backend-selftest:
	cd ../../backend/java/portal && mvn -q -DskipTests test-compile
	cd ../../backend/java/portal && java -ea -cp services/auth-service/target/test-classes:services/auth-service/target/classes com.portal.auth.AuthServiceSelfTest

frontend-test:
	cd ../../frontend/javascripts/portal && npm ci --no-audit --no-fund && npm test

rust-test:
	cd ../../backend/rust/database-conector-sqlite && cargo test --quiet

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
