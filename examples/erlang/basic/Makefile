.PHONY: clean
clean:
	rebar3 clean
	rm -rf _build build *.dump *.lock *.log src/generated/*
	
.PHONY: build
build:
	# 
	# Build
	#
	smithy build
	tree build/
	mkdir -p src/generated
	#
	# Copy the generated files to the src/generated directory
	#
	cp -r build/smithy/source/erlang-types-codegen/* src/generated/
	tree src/generated

.PHONY: deps
deps:
	rebar3 get-deps

.PHONY: test
test: deps
	rebar3 eunit

.PHONY: demo
demo: build test
