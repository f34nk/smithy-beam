X:=$(shell find examples/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \;)
EXAMPLES:=$(foreach x,$(X),$(x)/)
EXAMPLES_COUNT:=$(words $(EXAMPLES))

Y:=$(shell find baseline/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \;)
BASELINE:=$(foreach x,$(Y),$(x)/)
BASELINE_COUNT:=$(words $(BASELINE))

PARALLEL_JOBS:=10

.PHONY: all
all: clean build test

.PHONY: build
build:
	#
	# Build and publish the generator
	#
	rm -rf build-errors.log
	./gradlew clean build publishToMavenLocal 2>build-errors.log
	[ -s build-errors.log ] || rm -rf build-errors.log
	tree */build/libs
	tree ~/.m2/repository/io/smithy/beam

.PHONY: test
test: test/java
# test: test/java test/runtime-erlang test/runtime-elixir

.PHONY: test/java
test/java:
	#
	# Run JAVA tests
	#
	rm -rf test-errors.log
	./gradlew test 2>test-errors.log
	[ -s test-errors.log ] || rm -rf test-errors.log

.PHONY: test/runtime-erlang
test/runtime-erlang:
	#
	# Run runtime-erlang tests
	#
	logfile="$$(pwd)/runtime-erlang-test.log" && \
	temp="$$(pwd)/build/runtime-erlang" && \
	rm -rf "$$temp" "$$logfile" && \
	mkdir -p "$$temp/test" && \
	find runtime-erlang/*/* -type f -name *.erl -exec cp {} "$$temp/test/" \; && \
	echo \
	{erl_opts, [debug_info]}.\\n\
	{deps, [{jsx, \"3.1.0\"}]}.\\n\
	{eunit_opts, [verbose]}. > "$$temp/rebar.config" && \
    tree $$temp && \
    cd "$$temp" && \
	echo "Running: runtime-erlang tests" && \
    find test/ -type f -name "*_test.erl" | \
    xargs -I {} basename {} | \
    sed 's/_test.erl/_test/g' | \
    xargs -I {} echo "rebar3 eunit --module={}" | \
    xargs -I {} sh -c {} > "$$logfile"; \
	if grep -E "failed|syntax error" "$$logfile"; then \
		echo "$$(basename $$logfile) ...failed" ; \
		exit 1 ; \
	else \
		echo "$$(basename $$logfile) ...ok" ; \
	fi

    # 1. Find all test modules
    # 2. Get the base name of the test module
    # 3. Remove the _test.erl suffix
    # 4. Echo the command to run the test module
    # 5. Execute the command

.PHONY: test/runtime-elixir
test/runtime-elixir:
	#
	# Run runtime-elixir tests
	#
	logfile="$$(pwd)/runtime-elixir-test.log" && \
	temp="$$(pwd)/build/runtime-elixir" && \
	rm -rf "$$temp" "$$logfile" && \
	mkdir -p "$$temp/test" && \
	mkdir -p "$$temp/lib" && \
	find runtime-elixir/*/* -type f -name *.exs -exec cp {} "$$temp/test/" \; && \
	find runtime-elixir/*/* -type f -name *.ex -exec cp {} "$$temp/lib/" \; && \
	echo \
	defmodule Foo.MixProject do\\n\
  		use Mix.Project\\n\
		def project do\\n\
			[app: :foo, version: \"0.1.0\", elixir: \"~\> 1.19\", deps: deps\(\)]\\n\
		end\\n\
		def application do\\n\
			[extra_applications: [:logger, :crypto, :xmerl]]\\n\
		end\\n\
		defp deps do\\n\
			[{:plug, \"~\> 1.16\"}, {:jason, \"~\> 1.4\"}, {:req, \"~\> 0.5\"}]\\n\
		end\\n\
	end > "$$temp/mix.exs" && \
	echo \
	ExUnit.start\(\) > "$$temp/test/test_helper.exs" && \
	tree $$temp && \
    cd "$$temp" && \
	echo "Running: runtime-elixir tests" && \
	elixir -S mix deps.get > "$$logfile" 2>&1 && \
	elixir -S mix test >> "$$logfile" 2>&1 ;\
	if grep -E "stacktrace|CompileError" "$$logfile"; then \
		echo "$$(basename $$logfile) ...failed" ; \
		exit 1 ; \
	else \
		echo "$$(basename $$logfile) ...ok" ; \
	fi

.PHONY: clean
clean:
	#
	# Clean the build
	#
	rm -rf build bin codegen/build codegen/codegen-*/build codegen/codegen-*/bin *.log
	rm -rf ~/.m2/repository/io/smithy/beam

# Usage: make baseline/clean
# Usage: make examples/clean
.PHONY: %/clean
%/clean:
	#
	# Clean $@
	#
	target="$$(dirname $@)"; \
	if [ "$$target" = "baseline" ]; then \
		files="$(BASELINE)"; \
	elif [ "$$target" = "examples" ]; then \
		files="$(EXAMPLES)"; \
	else \
		echo "Unknown target: $$target" ; \
		exit 1 ; \
	fi; \
	if [ -z "$$files" ]; then \
		echo "No files to clean for target: $$target" ; \
		exit 1 ; \
	fi; \
	for x in $$files; do \
		echo ; \
		echo "Cleaning: $$x" ; \
		cd $$x ; \
		make clean ; \
		cd - >/dev/null; \
	done

# Usage: TARGET=baseline make run
.PHONY: run
run:
	mkdir -p build
	rm -rf build/*.log
	touch build/$(TARGET).log
	#
	# Run $(TARGET) in parallel ($(PARALLEL_JOBS) jobs)
	#
	find $(TARGET)/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \; |\
	xargs -S1024 -P $(PARALLEL_JOBS) -I {} sh -c ' \
		target="{}"; \
		name="$$(basename $$target)"; \
		lang="$$(echo $$target | cut -d/ -f2)"; \
		logfile="build/$(TARGET)-$$lang-$$name.log"; \
		sleep 1; \
		echo "Running: $$target" ; \
		make $$target > $$logfile 2>&1; \
		if grep -q "make.*Error" $$logfile; then \
			printf "F";\
			echo "$$logfile ...failed" >> build/$(TARGET).log; \
			exit 1 ; \
		else \
			printf ".";\
			echo "$$logfile ...ok" >> build/$(TARGET).log; \
		fi; \
	'; \
	STATUS=$$?; \
	echo; \
	cat build/$(TARGET).log; \
	echo; \
	exit $$STATUS; \

# Usage: make examples
.PHONY: examples
examples:
	TARGET=examples make run

# Usage: make examples/erlang/weather-service
.PHONY: $(EXAMPLES)
examples/%: $(EXAMPLES)
	#
	# Build $@
	#
	cd $@ && make clean && time make demo

# Usage: make baseline
.PHONY: baseline
baseline:
	TARGET=baseline make run

# Usage: make baseline/erlang/weather-service
.PHONY: $(BASELINE)
baseline/%: $(BASELINE)
	#
	# Build $@
	#
	cd $@ && make clean && time make test
