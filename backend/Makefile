.PHONY: build-java build-rust

build-java:
	cd java/portal && mvn -q -DskipTests package

build-rust:
	cd rust/database-conector-sqlite && cargo build --release
