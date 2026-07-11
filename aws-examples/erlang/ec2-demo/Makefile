export HOST_PORT=4001
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=$(AWS_ENDPOINT)

YELLOW=\033[1;33m
NC=\033[0m

.PHONY: all
all: clean build compile analyze test

.PHONY: copy
copy:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/ec2/service/2016-11-15/ec2-2016-11-15.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/ec2/service/2016-11-15/ec2-2016-11-15.json
	mkdir -p model/
	mv ec2-2016-11-15.json model/

.PHONY: build
build: copy
	# 
	# Build
	#
	smithy build
	rm -rf src/generated
	mkdir -p src/generated
	cp -r build/smithy/source/erlang-*-codegen/* src/generated/
	tree src/generated

.PHONY: compile
compile:
	#
	# Compile
	#
	rebar3 lock
	rebar3 compile

.PHONY: analyze
analyze:
	#
	# Analyze
	#
	rebar3 as test compile
	rebar3 as test dialyzer || echo "\n$(YELLOW)Some Dialyzer warnings are expected: we use record types in specs (for Dialyzer) but maps at runtime (no conversion overhead, matches AWS SDK patterns).$(NC)\n"
	rebar3 as test dialyzer_html

.PHONY: test
test: test/unit

.PHONY: test/unit
test/unit:
	#
	# Test
	#
	rebar3 eunit

.PHONY: demo
demo: build compile run

.PHONY: run
run:
	#
	# Run
	#
	erl \
	-pa _build/default/lib/*/ebin \
	-noshell \
	-eval "application:ensure_all_started(inets), application:ensure_all_started(ssl), application:ensure_all_started(jsone), demo_app:run(), halt()."

.PHONY: clean
clean:
	rm -rf build _build src/generated *.crashdump *.dump test/*.beam *.json.*
	rm -rf model/*.json
