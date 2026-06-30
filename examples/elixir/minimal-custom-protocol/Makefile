ROOT := ../../..
export HEX_HOME := $(shell pwd)/.hex
export HEX_HTTP_CONCURRENCY := 1
export HEX_HTTP_TIMEOUT := 120

.PHONY: all
all: clean build

.PHONY: clean
clean:
	mix clean
	rm -rf _build build deps .hex *.lock *.log lib/generated/*
	cd $(ROOT) && ./gradlew -p examples/elixir/minimal-custom-protocol/codegen clean

.PHONY: codegen-jar
codegen-jar:
	cd $(ROOT) && ./gradlew -p examples/elixir/minimal-custom-protocol/codegen publishToMavenLocal

.PHONY: build
build: codegen-jar
	smithy build
	mkdir -p lib/generated
	cp -r build/smithy/source/elixir-*-codegen/* lib/generated/

.PHONY: deps
deps:
	mix deps.get

.PHONY: test
test: deps
	mix test

.PHONY: demo
demo: build test
