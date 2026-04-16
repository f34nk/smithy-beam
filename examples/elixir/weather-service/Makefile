YELLOW=\033[1;33m
NC=\033[0m

.PHONY: all
all: clean generate compile test

.PHONY: generate
generate:
	#
	# Generate Elixir server
	#
	smithy build
	tree lib/generated

.PHONY: compile
compile:
	#
	# Compile Elixir server
	#
	mix deps.get
	mix compile

.PHONY: test
test:
	#
	# Test Elixir server
	#
	mix test

.PHONY: clean
clean:
	rm -rf lib/generated _build deps .smithy build/

.PHONY: demo
demo: clean generate compile test
