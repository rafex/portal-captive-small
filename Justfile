set shell := ["bash", "-cu"]

version := "0.1.0"

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

tag-create tag:
    git tag -a {{tag}} -m "Release {{tag}}"

tag-push tag:
    git push origin {{tag}}

tag-repush tag:
    git push origin :refs/tags/{{tag}} || true
    git push origin {{tag}}
