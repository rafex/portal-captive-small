.PHONY: validate build package clean release-local

VERSION ?= 0.1.0
DIST_DIR := dist

validate:
	$(MAKE) -C scripts
	$(MAKE) -C containers lint
	$(MAKE) -C sql validate

build:
	$(MAKE) -C frontend build
	$(MAKE) -C backend build-java
	$(MAKE) -C backend build-rust

package: build
	mkdir -p $(DIST_DIR)
	tar -czf $(DIST_DIR)/frontend-$(VERSION).tar.gz -C frontend/javascripts/portal dist
	sha256sum $(DIST_DIR)/frontend-$(VERSION).tar.gz > $(DIST_DIR)/frontend-$(VERSION).tar.gz.sha256
	tar -czf $(DIST_DIR)/backend-$(VERSION).tar.gz backend/java/portal backend/rust/database-conector-sqlite/target/release
	sha256sum $(DIST_DIR)/backend-$(VERSION).tar.gz > $(DIST_DIR)/backend-$(VERSION).tar.gz.sha256
	cp scripts/install/script-install.sh $(DIST_DIR)/script-install.sh

release-local: package
	@echo "Artefactos disponibles en $(DIST_DIR)"

clean:
	rm -rf $(DIST_DIR)
