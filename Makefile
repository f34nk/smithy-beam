X := $(shell find aws-examples examples baseline -maxdepth 3 -name Makefile -type f -exec dirname {} \;)
EXAMPLES := $(foreach x,$(X),$(x)/)
EXAMPLES_COUNT := $(words $(EXAMPLES))

PARALLEL_JOBS := 5
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
	make -C runtime/erlang test

.PHONY: test/java
test/java:
	#
	# Run JAVA tests
	#
	rm -rf test-errors.log
	./gradlew test 2>test-errors.log
	[ -s test-errors.log ] || rm -rf test-errors.log

.PHONY: format/java
format/java:
	#
	# Format all Java source files
	#
	./gradlew spotlessApply
	
.PHONY: format/runtime-erlang
format/runtime-erlang:
	make -C runtime/erlang format

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
	find $$target -name Makefile -type f -maxdepth 3 -exec dirname {} \; |\
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
	find $$target -name Makefile -type f -maxdepth 3 -exec dirname {} \; |\
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
	@if find $(TARGET)/erlang -maxdepth 2 -name Makefile 2>/dev/null | grep -q .; then \
		make -C runtime/erlang compile; \
	fi
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
	@case "$(DEMO)" in \
		aws-examples/*) \
			PORT=$$(grep -m1 '^export HOST_PORT=' "$(DEMO)/Makefile" 2>/dev/null | cut -d= -f2); \
			if [ -n "$$PORT" ] && ! curl -sf "http://localhost:$$PORT/_localstack/health" >/dev/null 2>&1; then \
				echo "ERROR: LocalStack not reachable at http://localhost:$$PORT for $(DEMO)"; \
				echo "Port may be in use by another process. Check: lsof -i :$$PORT"; \
				exit 1; \
			fi ;; \
	esac
	cd $(DEMO) && time make demo

.PHONY: _aws-examples
_aws-examples:
	TARGET=aws-examples make _run

# Usage: make aws-examples
.PHONY: aws-examples
aws-examples: docker/restart _aws-examples docker/stop

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
		-p 3000:4566 \
		-p 3001:4566 \
		-p 3002:4566 \
		-p 3003:4566 \
		-p 3004:4566 \
		-p 3005:4566 \
		-p 3006:4566 \
		-p 3007:4566 \
		-p 3008:4566 \
		-p 3009:4566 \
		-p 3010:4566 \
		-p 3011:4566 \
		-p 4000:4566 \
		-p 4001:4566 \
		-p 4002:4566 \
		-p 4003:4566 \
		-p 4004:4566 \
		-p 4005:4566 \
		-p 4006:4566 \
		-p 4007:4566 \
		-p 4008:4566 \
		-p 4009:4566 \
		-p 4010:4566 \
		-p 4011:4566 \
		-e SERVICES=apigateway,cloudformation,cloudwatch,dynamodb,ec2,elasticache,es,events,firehose,iam,kinesis,lambda,logs,redshift,route53,s3,s3control,secretsmanager,sm,sns,sqs,ssm,sts,stepfunctions \
		localstack/localstack
	make docker/wait
	
.PHONY: docker/stop
docker/stop:
	#
	# Stop LocalStack
	#
	@if docker ps -q -f name=^/$(CONTAINER_NAME)$$ | grep -q .; then \
		echo "Container $(CONTAINER_NAME) is running, stopping..."; \
		docker stop $(CONTAINER_NAME); \
	elif docker ps -aq -f name=^/$(CONTAINER_NAME)$$ | grep -q .; then \
		echo "Container $(CONTAINER_NAME) is stopped, removing..."; \
		docker rm $(CONTAINER_NAME) 2>/dev/null || true; \
	else \
		exit 0; \
	fi
	@for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do \
		if ! docker ps -aq -f name=^/$(CONTAINER_NAME)$$ | grep -q .; then \
			exit 0; \
		fi; \
		sleep 1; \
	done; \
	echo "Container $(CONTAINER_NAME) did not stop in time"; \
	exit 1

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

.PHONY: docker/restart
docker/restart: docker/stop docker/start
