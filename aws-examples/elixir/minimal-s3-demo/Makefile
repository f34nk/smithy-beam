export HOST_PORT=4567
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT=http://localhost:$(HOST_PORT)
export TF_VAR_endpoint=http://localhost:$(HOST_PORT)
export SERVICES=s3
export CONTAINER_NAME=$(SERVICES)-demo-elixir
export HEX_HOME := $(shell pwd)/.hex

.PHONY: clean
clean: docker/stop
	mix clean
	rm -rf _build build deps .hex *.lock *.log lib/generated/*
	rm -rf terraform/.terraform terraform/terraform.tfstate terraform/terraform.tfstate.backup terraform/.terraform.lock.hcl

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
demo: build
	make docker/start && make docker/wait && make terraform && make test && make docker/stop

.PHONY: terraform
terraform:
	@echo "AWS_DEFAULT_REGION is $$AWS_DEFAULT_REGION"
	cd terraform && terraform init && terraform apply -auto-approve

.PHONY: docker/start
docker/start:
	@if curl -sf "http://localhost:$(HOST_PORT)/_localstack/health" >/dev/null 2>&1; then \
		echo "LocalStack already listening on port $(HOST_PORT)"; \
	else \
		echo "Forward port $(HOST_PORT) to Localstack port 4566"; \
		docker run --rm -d -p $(HOST_PORT):4566 -e SERVICES=$(SERVICES) --name $(CONTAINER_NAME) localstack/localstack; \
	fi

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

.PHONY: docker/stop
docker/stop:
	docker stop $(CONTAINER_NAME) &> /dev/null || true
	docker rm $(CONTAINER_NAME) &> /dev/null || true
