.PHONY: clean
clean:
	rebar3 clean
	rm -rf _build build *.dump *.log src/generated
	
.PHONY: build
build:
	# 
	# Build
	#
	rm -rf build/smithy/source/erlang-*-codegen
	smithy build
	tree build/
	mkdir -p src/generated
	rm -rf src/generated/*
	#
	# Copy the generated files to the src/generated directory
	#
	cp -r build/smithy/source/erlang-*-codegen/* src/generated/
	tree src/generated

.PHONY: test
test:
	rebar3 eunit

.PHONY: demo
demo: build test
