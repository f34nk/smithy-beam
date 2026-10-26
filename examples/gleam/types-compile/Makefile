ROOT := $(abspath ../../..)

.PHONY: clean
clean:
	rm -rf build src/*.gleam build/dev build/erlang-shipment .gleam_deps gleam.lock

.PHONY: publish-plugin
publish-plugin:
	cd $(ROOT) && ./gradlew :codegen:codegen-gleam:publishToMavenLocal

.PHONY: generate
generate: publish-plugin
	smithy build
	mkdir -p src
	rm -f src/*.gleam
	cp -f build/smithy/source/gleam-*-codegen/*.gleam src/

.PHONY: build
build: generate
	@if command -v gleam >/dev/null 2>&1; then \
		gleam build; \
	else \
		echo "Gleam CLI not found; skipping gleam build (optional prerequisite)."; \
	fi
