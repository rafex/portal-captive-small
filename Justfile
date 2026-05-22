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
