export HOST_PORT=3009
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export HEX_HOME := $(shell pwd)/.hex
export HEX_HTTP_CONCURRENCY := 1
export HEX_HTTP_TIMEOUT := 120

YELLOW=\033[1;33m
NC=\033[0m

.PHONY: all
all: clean build compile test

.PHONY: copy
copy:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/sns/service/2010-03-31/sns-2010-03-31.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/sns/service/2010-03-31/sns-2010-03-31.json
	mkdir -p model/
	mv sns-2010-03-31.json model/

.PHONY: build
build: copy
	#
	# Build
	#
	smithy build
	mkdir -p lib/generated
	cp -r build/smithy/source/elixir-*-codegen/* lib/generated/
	tree -a lib/generated

.PHONY: compile
compile:
	#
	# Compile
	#
	mix deps.get
	mix compile

.PHONY: test
test: test/unit

.PHONY: test/unit
test/unit:
	#
	# Test
	#
	mix test

.PHONY: demo
demo: build compile test run

.PHONY: run
run:
	#
	# Run
	#
	mix run -e "Demo.run()"

.PHONY: clean
clean:
	rm -rf lib/generated _build deps .smithy build/ *.dump *.json.*
	rm -rf model/*.json
