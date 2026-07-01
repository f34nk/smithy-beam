export HOST_PORT=3006
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=http://localhost:$(HOST_PORT)
export HEX_HOME := $(shell pwd)/.hex
export HEX_HTTP_CONCURRENCY := 1
export HEX_HTTP_TIMEOUT := 120

.PHONY: clean
clean:
	mix clean
	rm -rf _build build deps .hex *.lock *.log lib/generated/*
	rm -rf terraform/.terraform* terraform/terraform.*

.PHONY: build
build:
	smithy build
	mkdir -p lib/generated
	cp -r build/smithy/source/elixir-*-codegen/* lib/generated/

.PHONY: deps
deps:
	mix deps.get

.PHONY: test
test: deps
	mix test

.PHONY: demo
demo: build terraform test

.PHONY: terraform
terraform:
	@echo "AWS_DEFAULT_REGION is $$AWS_DEFAULT_REGION"
	cd terraform && terraform init && terraform apply -auto-approve
