.PHONY: deps
deps:
	gleam deps download

.PHONY: test
test: deps
	gleam test

.PHONY: build
build: deps
	gleam build

.PHONY: clean
clean:
	rm -rf build

.PHONY: format
format:
	gleam format
