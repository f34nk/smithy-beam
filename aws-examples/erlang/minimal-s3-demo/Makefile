export HOST_PORT=4577
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=http://localhost:$(HOST_PORT)

.PHONY: clean
clean:
	rebar3 clean
	rm -rf _build build *.dump *.log src/generated/*
	rm -rf terraform/.terraform* terraform/terraform.*

.PHONY: build
build:
	smithy build
	mkdir -p src/generated
	cp -r build/smithy/source/erlang-*-codegen/* src/generated/

.PHONY: deps
deps:
	rebar3 get-deps

.PHONY: test
test: deps
	rebar3 eunit

.PHONY: demo
demo: build terraform test

.PHONY: terraform
terraform:
	@echo "AWS_DEFAULT_REGION is $$AWS_DEFAULT_REGION"
	cd terraform && terraform init && terraform apply -auto-approve
