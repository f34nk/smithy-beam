.PHONY: compile
compile:
	mix compile

.PHONY: deps
deps:
	mix deps.get

.PHONY: test
test: deps
	mix test

.PHONY: build
build: deps compile

.PHONY: clean
clean:
	mix clean
	rm -rf _build build deps .hex *.lock *.log

.PHONY: format
format:
	mix format
