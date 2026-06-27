export HOST_PORT=4576
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=$(AWS_ENDPOINT)

YELLOW=\033[1;33m
NC=\033[0m

.PHONY: all
all: clean build compile test

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
	mkdir -p lib/generated
	cp -r build/smithy/source/elixir-*-codegen/* lib/generated/
	tree lib/generated

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
demo: build
	#
	# TODO: fix compilation
	#

# .PHONY: demo
# demo: build compile
# 	make docker/start && make docker/wait && make terraform && make run && make docker/stop

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
	rm -rf rm -rf terraform/.terraform* terraform/terraform.*

.PHONY: terraform
terraform:
	#
	# Create infrastructure
	#
	@echo "AWS_DEFAULT_REGION is $$AWS_DEFAULT_REGION"
	cd terraform && terraform init && terraform apply -auto-approve
