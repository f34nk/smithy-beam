ROOT := $(abspath ../../..)

.PHONY: clean
clean:
	rm -rf build src/basic_service_*.gleam build/dev build/erlang-shipment .gleam_deps

.PHONY: publish-plugin
publish-plugin:
	cd $(ROOT) && ./gradlew :beam-lang:publishToMavenLocal :codegen:codegen-core:publishToMavenLocal :codegen:codegen-gleam:publishToMavenLocal

.PHONY: generate
generate: publish-plugin
	smithy build
	mkdir -p src
	rm -f src/basic_service_*.gleam
	cp -f build/smithy/source/gleam-server-codegen/*.gleam src/

.PHONY: build
build: generate
	@if command -v gleam >/dev/null 2>&1; then \
		gleam deps download && gleam build; \
	else \
		echo "Gleam CLI not found; skipping gleam build (optional prerequisite)."; \
	fi

.PHONY: demo
demo: build
	@if command -v gleam >/dev/null 2>&1; then \
		gleam run; \
	else \
		echo "Gleam CLI not found; skipping gleam run."; \
	fi
