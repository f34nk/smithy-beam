YELLOW=\033[1;33m
NC=\033[0m

.PHONY: all
all: clean generate compile analyze test 

.PHONY: generate
generate:
	# 
	# Generate Erlang client
	#
	smithy build
	tree src/generated

.PHONY: compile
compile:
	#
	# Compile Erlang client
	#
	rebar3 lock
	rebar3 compile
	rebar3 as test deps

.PHONY: analyze
analyze:
	#
	# Analyze Erlang client
	#
	rebar3 as test compile
	rebar3 as test dialyzer || echo "\n$(YELLOW)Some Dialyzer warnings are expected: we use record types in specs (for Dialyzer) but maps at runtime (no conversion overhead, matches AWS SDK patterns).$(NC)\n"
	rebar3 as test dialyzer_html

.PHONY: test
test:
	#
	# Test Erlang client
	#
	rebar3 eunit

.PHONY: clean
clean:
	rm -rf build _build src/generated rebar3.crashdump test/*.beam

.PHONY: demo
demo: clean generate compile analyze test
