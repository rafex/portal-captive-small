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
