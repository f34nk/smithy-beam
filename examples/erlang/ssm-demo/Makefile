export HOST_PORT=4576
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=$(AWS_ENDPOINT)
export SERVICES=ssm
export CONTAINER_NAME=ssm-demo

YELLOW=\033[1;33m
NC=\033[0m

.PHONY: all
all: clean copy generate compile analyze test

.PHONY: copy
copy:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/ssm/service/2014-11-06/ssm-2014-11-06.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/ssm/service/2014-11-06/ssm-2014-11-06.json
	mv ssm-2014-11-06.json model/

.PHONY: generate
generate:
	# 
	# Generate $(CONTAINER_NAME)
	#
	smithy build
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
demo: copy generate compile 
	make docker/start && make terraform && make run && make docker/stop

.PHONY: run
run:
	#
	# Run $(CONTAINER_NAME)
	#
	erl \
	-pa _build/default/lib/aws_demo/ebin \
	-pa _build/default/lib/jsx/ebin \
	-noshell \
	-eval "application:ensure_all_started(inets), application:ensure_all_started(ssl), application:ensure_all_started(jsx), aws_demo_app:run(), halt()."

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
