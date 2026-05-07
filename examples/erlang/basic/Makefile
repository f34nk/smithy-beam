.PHONY: clean
clean:
	rebar3 clean
	rm -rf _build build *.dump *.lock *.log
	
.PHONY: build
build:
	# 
	# Build
	#
	smithy build
	tree build/

.PHONY: deps
deps:
	rebar3 get-deps

.PHONY: test
test: deps
	rebar3 eunit

.PHONY: demo
demo: build test
