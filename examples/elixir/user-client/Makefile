export HEX_HOME := $(shell pwd)/.hex
export HEX_HTTP_CONCURRENCY := 1
export HEX_HTTP_TIMEOUT := 120

.PHONY: clean
clean:
	mix clean
	rm -rf _build build deps .hex *.lock *.log lib/generated/*

.PHONY: build
build:
	#
	# Build
	#
	smithy build
	tree build/
	mkdir -p lib/generated
	#
	# Copy the generated files to the lib/generated directory
	#
	cp -r build/smithy/source/elixir-*-codegen/* lib/generated/
	tree lib/generated

.PHONY: deps
deps:
	mix deps.get

.PHONY: test
test: deps
	mix test

.PHONY: demo
demo: build test
