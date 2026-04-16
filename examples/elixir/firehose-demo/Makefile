export HOST_PORT=4582
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=$(AWS_ENDPOINT)
export SERVICES=firehose
export CONTAINER_NAME=firehose-demo-elixir

YELLOW=\033[1;33m
NC=\033[0m

.PHONY: all
all: clean copy generate compile test

.PHONY: copy
copy:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/firehose/service/2015-08-04/firehose-2015-08-04.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/firehose/service/2015-08-04/firehose-2015-08-04.json
	mv firehose-2015-08-04.json model/

.PHONY: generate
generate:
	#
	# Generate $(CONTAINER_NAME)
	#
	smithy build
	tree lib/generated

.PHONY: compile
compile:
	#
	# Compile $(CONTAINER_NAME)
	#
	mix deps.get
	mix compile

.PHONY: test
test: test/unit

.PHONY: test/unit
test/unit:
	#
	# Test $(CONTAINER_NAME)
	#
	mix test

.PHONY: demo
demo: copy generate compile
	make docker/start && make terraform && make run && make docker/stop

.PHONY: run
run:
	#
	# Run $(CONTAINER_NAME)
	#
	mix run -e "AwsDemo.run()"

.PHONY: clean
clean: docker/stop
	rm -rf lib/generated _build deps .smithy build/
	rm -rf model/*.json

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
