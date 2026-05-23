.PHONY: build-java build-rust

build-java:
	cd java/portal && MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" ./mvnw -q -DskipTests package

build-rust:
	cd rust/database-conector-sqlite && cargo build --release
