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

tag-create tag:
    bash scripts/git/tag-create.sh {{tag}}
    if ! git diff --quiet -- CHANGE.md RELEASE.md; then git add CHANGE.md RELEASE.md; git commit -m "docs(release): prepare {{tag}}"; fi
    git tag -a {{tag}} -m "Release {{tag}}"

tag-push tag:
    git push origin {{tag}}

tag-repush tag:
    git push origin :refs/tags/{{tag}} || true
    git push origin {{tag}}
