ROOT := ../../..

.PHONY: all
all: clean build

.PHONY: clean
clean:
	rebar3 clean
	rm -rf _build build *.dump *.log src/generated/*
	cd $(ROOT) && ./gradlew -p examples/erlang/minimal-custom-protocol/codegen clean

.PHONY: codegen-jar
codegen-jar:
#	cd $(ROOT) && ./gradlew :codegen:codegen-erlang:publishToMavenLocal
	cd $(ROOT) && ./gradlew -p examples/erlang/minimal-custom-protocol/codegen publishToMavenLocal

.PHONY: build
build: codegen-jar
	smithy build
	mkdir -p src/generated
	cp -r build/smithy/source/erlang-*-codegen/* src/generated/

.PHONY: deps
deps:
	rebar3 get-deps

.PHONY: test
test: deps
	rebar3 eunit

.PHONY: demo
demo: build test
