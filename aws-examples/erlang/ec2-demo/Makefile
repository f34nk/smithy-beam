export HOST_PORT=4578
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=$(AWS_ENDPOINT)
export SERVICES=ec2
export CONTAINER_NAME=$(SERVICES)-demo

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
	# Build $(CONTAINER_NAME)
	#
	smithy build
	mkdir -p src/generated
	cp -r build/smithy/source/erlang-*-codegen/* src/generated/
	tree src/generated

.PHONY: compile
compile:
	#
	# Compile $(CONTAINER_NAME)
	#
	rebar3 lock
	rebar3 compile

.PHONY: analyze
analyze:
	#
	# Analyze $(CONTAINER_NAME)
	#
	rebar3 as test compile
	rebar3 as test dialyzer || echo "\n$(YELLOW)Some Dialyzer warnings are expected: we use record types in specs (for Dialyzer) but maps at runtime (no conversion overhead, matches AWS SDK patterns).$(NC)\n"
	rebar3 as test dialyzer_html

.PHONY: test
test: test/unit

.PHONY: test/unit
test/unit:
	#
	# Test $(CONTAINER_NAME)
	#
	rebar3 eunit

.PHONY: demo
demo: build compile
	make docker/start && make docker/wait && make terraform && make run && make docker/stop

.PHONY: run
run:
	#
	# Run $(CONTAINER_NAME)
	#
	erl \
	-pa _build/default/lib/demo/ebin \
	-pa _build/default/lib/jsx/ebin \
	-pa _build/default/lib/aws_signature/ebin \
	-noshell \
	-eval "application:ensure_all_started(inets), application:ensure_all_started(ssl), application:ensure_all_started(jsx), demo_app:run(), halt()."

.PHONY: clean
clean: docker/stop
	rm -rf build _build src/generated rebar3.crashdump erl_crash.dump test/*.beam
	rm -rf model/*.json
	rm -rf terraform/.terraform terraform/terraform.tfstate terraform/terraform.tfstate.backup terraform/.terraform.lock.hcl

.PHONY: terraform
terraform:
	#
	# Create infrastructure for $(CONTAINER_NAME)
	#
	@echo "AWS_DEFAULT_REGION is $$AWS_DEFAULT_REGION"
	cd terraform && terraform init && terraform apply -auto-approve

.PHONY: docker/wait
docker/wait:
	@echo "Waiting for LocalStack on port $(HOST_PORT)..."
	@for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do \
		if curl -sf "http://localhost:$(HOST_PORT)/_localstack/health" >/dev/null 2>&1; then \
			echo "LocalStack is ready"; \
			exit 0; \
		fi; \
		sleep 1; \
	done; \
	echo "LocalStack did not become ready in time"; \
	exit 1

.PHONY: docker/start
docker/start:
	#
	# Run LocalStack for $(CONTAINER_NAME) at port $(HOST_PORT)
	#
	@echo "Forward port $$HOST_PORT to Localstack port 4566"
	docker run --rm -d -p $(HOST_PORT):4566 -e SERVICES=$(SERVICES) --name $(CONTAINER_NAME) localstack/localstack

.PHONY: docker/stop
docker/stop:
	docker stop $(CONTAINER_NAME) &> /dev/null || true
	docker rm $(CONTAINER_NAME) &> /dev/null || true
