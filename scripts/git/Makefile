.PHONY: run quality lint install-hooks uninstall-hooks

run: quality

quality:
	bash quality-local.sh

lint:
	bash pre-commit-lint.sh

install-hooks:
	bash install-githooks.sh

uninstall-hooks:
	bash uninstall-githooks.sh
