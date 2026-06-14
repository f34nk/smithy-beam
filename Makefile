X := $(shell find aws-examples examples baseline -maxdepth 3 -name Makefile -type f -exec dirname {} \;)
EXAMPLES := $(foreach x,$(X),$(x)/)
EXAMPLES_COUNT := $(words $(EXAMPLES))

PARALLEL_JOBS := 10
CONTAINER_NAME = examples-stack

.PHONY: all
all: clean build test

.PHONY: build
build:
	#
	# Build and publish the generator
	#
	rm -rf build-errors.log
	./gradlew clean build publishToMavenLocal 2>build-errors.log
	[ -s build-errors.log ] || rm -rf build-errors.log
	tree */build/libs
	tree ~/.m2/repository/io/smithy/beam

.PHONY: test
test: test/java
# test: test/java test/runtime-erlang test/runtime-elixir

.PHONY: test/java
test/java:
	#
	# Run JAVA tests
	#
	rm -rf test-errors.log
	./gradlew test 2>test-errors.log
	[ -s test-errors.log ] || rm -rf test-errors.log

.PHONY: test/runtime-erlang
test/runtime-erlang:
	#
	# Run runtime-erlang tests
	#
	logfile="$$(pwd)/runtime-erlang-test.log" && \
	temp="$$(pwd)/build/runtime-erlang" && \
	rm -rf "$$temp" "$$logfile" && \
	mkdir -p "$$temp/test" && \
	find runtime-erlang/*/* -type f -name *.erl -exec cp {} "$$temp/test/" \; && \
	echo \
	{erl_opts, [debug_info]}.\\n\
	{deps, [{jsx, \"3.1.0\"}]}.\\n\
	{eunit_opts, [verbose]}. > "$$temp/rebar.config" && \
    tree $$temp && \
    cd "$$temp" && \
	echo "Running: runtime-erlang tests" && \
    find test/ -type f -name "*_test.erl" | \
    xargs -I {} basename {} | \
    sed 's/_test.erl/_test/g' | \
    xargs -I {} echo "rebar3 eunit --module={}" | \
    xargs -I {} sh -c {} > "$$logfile"; \
	if grep -E "failed|syntax error" "$$logfile"; then \
		echo "$$(basename $$logfile) ...failed" ; \
		exit 1 ; \
	else \
		echo "$$(basename $$logfile) ...ok" ; \
	fi

    # 1. Find all test modules
    # 2. Get the base name of the test module
    # 3. Remove the _test.erl suffix
    # 4. Echo the command to run the test module
    # 5. Execute the command

.PHONY: test/runtime-elixir
test/runtime-elixir:
	#
	# Run runtime-elixir tests
	#
	logfile="$$(pwd)/runtime-elixir-test.log" && \
	temp="$$(pwd)/build/runtime-elixir" && \
	rm -rf "$$temp" "$$logfile" && \
	mkdir -p "$$temp/test" && \
	mkdir -p "$$temp/lib" && \
	find runtime-elixir/*/* -type f -name *.exs -exec cp {} "$$temp/test/" \; && \
	find runtime-elixir/*/* -type f -name *.ex -exec cp {} "$$temp/lib/" \; && \
	echo \
	defmodule Foo.MixProject do\\n\
  		use Mix.Project\\n\
		def project do\\n\
			[app: :foo, version: \"0.1.0\", elixir: \"~\> 1.19\", deps: deps\(\)]\\n\
		end\\n\
		def application do\\n\
			[extra_applications: [:logger, :crypto, :xmerl]]\\n\
		end\\n\
		defp deps do\\n\
			[{:plug, \"~\> 1.16\"}, {:jason, \"~\> 1.4\"}, {:req, \"~\> 0.5\"}]\\n\
		end\\n\
	end > "$$temp/mix.exs" && \
	echo \
	ExUnit.start\(\) > "$$temp/test/test_helper.exs" && \
	tree $$temp && \
    cd "$$temp" && \
	echo "Running: runtime-elixir tests" && \
	elixir -S mix deps.get > "$$logfile" 2>&1 && \
	elixir -S mix test >> "$$logfile" 2>&1 ;\
	if grep -E "stacktrace|CompileError" "$$logfile"; then \
		echo "$$(basename $$logfile) ...failed" ; \
		exit 1 ; \
	else \
		echo "$$(basename $$logfile) ...ok" ; \
	fi

.PHONY: clean
clean:
	#
	# Clean the build
	#
	rm -rf build bin codegen/build codegen/codegen-*/build codegen/codegen-*/bin *.log
	rm -rf ~/.m2/repository/io/smithy/beam

# Usage: make examples/clean
.PHONY: %/clean
%/clean:
	#
	# Clean $@
	#
	target="$$(dirname $@)"; \
	find $$target/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \; |\
	xargs -S1024 -P $(PARALLEL_JOBS) -I {} sh -c ' \
		target="{}"; \
		echo "Clean $$target"; \
		cd $$target && make clean; \
	'; \

# Usage: make examples/build
.PHONY: %/build
%/build:
	#
	# Build $@
	#
	target="$$(dirname $@)"; \
	find $$target/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \; |\
	xargs -S1024 -P $(PARALLEL_JOBS) -I {} sh -c ' \
		target="{}"; \
		sleep 1; \
		echo "Build $$target"; \
		cd $$target && make clean && make build; \
	'; \

# Usage: TARGET=examples make _run
.PHONY: _run
_run:
	mkdir -p build
	rm -rf build/$(TARGET)*.log
	touch build/$(TARGET).log
	#
	# Run $(TARGET) in parallel ($(PARALLEL_JOBS) jobs)
	#
	find $(TARGET)/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \; |\
	xargs -S1024 -P $(PARALLEL_JOBS) -I {} sh -c ' \
		target="{}"; \
		sleep 1; \
		make $$target; \
	'; \
	STATUS=$$?; \
	echo; \
	cat build/$(TARGET).log; \
	echo; \
	exit $$STATUS; \

.PHONY: _demo
_demo:
	#
	# Build $(DEMO)
	#
	cd $(DEMO) && make clean && time make demo

.PHONY: _aws-examples
_aws-examples:
	TARGET=aws-examples make _run

# Usage: make aws-examples
.PHONY: aws-examples
aws-examples: docker/stop docker/start _aws-examples docker/stop

# Usage: make examples
.PHONY: examples
examples:
	TARGET=examples make _run

# Usage: make examples
.PHONY: baseline
baseline:
	TARGET=baseline make _run

# Usage: make examples/erlang/weather-service
.SILENT:
.PHONY: $(EXAMPLES)
aws-examples/% examples/% baseline/%: $(EXAMPLES)
	target="$@"; \
	dirname="$$(echo $$target|cut -d/ -f1)"; \
	lang="$$(echo $$target|cut -d/ -f2)"; \
	name="$$(echo $$target|cut -d/ -f3)"; \
	build_log=build/$$dirname.log; \
	logfile="build/$$dirname-$$lang-$$name.log"; \
	echo "Running $$target > $$logfile"; \
	DEMO=$$target make _demo > $$logfile 2>&1; \
	if grep -q "make.*Error" $$logfile; then \
		echo "$$logfile ...failed" >> $$build_log; \
		exit 1 ; \
	else \
		printf ".";\
		echo "$$logfile ...ok" >> $$build_log; \
	fi; \

.PHONY: docker/start
docker/start:
	#
	# Run LocalStack
	#
	docker run --rm -d \
		--name $(CONTAINER_NAME) \
		-v /var/run/docker.sock:/var/run/docker.sock \
		-p 4566:4566 \
		-p 4576:4566 \
		-p 4577:4566 \
		-p 4578:4566 \
		-p 4579:4566 \
		-p 4580:4566 \
		-p 4581:4566 \
		-p 4582:4566 \
		-e SERVICES=s3,sqs,dynamodb,firehose,kinesis,lambda,apigateway,cloudformation,cloudwatch,ec2,iam,logs,redshift,route53,events,sns,sts,sm,es,elasticache,secretsmanager,stepfunctions,s3control \
		localstack/localstack
	make docker/wait
	
.PHONY: docker/stop
docker/stop:
	#
	# Stop LocalStack
	#
	docker stop $(CONTAINER_NAME) &> /dev/null || true
	docker rm $(CONTAINER_NAME) &> /dev/null || true

.PHONY: docker/wait
docker/wait:
	@echo "Waiting for LocalStack on port 4566..."
	@for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do \
		if curl -sf "http://localhost:4566/_localstack/health" >/dev/null 2>&1; then \
			echo "LocalStack is ready"; \
			exit 0; \
		fi; \
		sleep 1; \
	done; \
	echo "LocalStack did not become ready in time"; \
	exit 1
