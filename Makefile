X:=$(shell find examples/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \;)
EXAMPLES:=$(foreach x,$(X),$(x)/)
EXAMPLES_COUNT:=$(words $(EXAMPLES))

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
test: test/java test/runtime-erlang

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
	logfile="$$(pwd)/erlang-runtime-test.log" && \
	temp="$$(pwd)/build/tmp" && \
	rm -rf "$$temp" "$$logfile" && \
	mkdir -p "$$temp/test" && \
	find runtime-erlang/*/* -type f -name *.erl -exec cp {} "$$temp/test/" \; && \
	echo \
	{erl_opts, [debug_info]}.\\n\
	{deps, [{jsx, \"3.1.0\"}]}.\\n\
	{eunit_opts, [verbose]}. > "$$temp/rebar.config" && \
    tree $$temp && \
    cd "$$temp" && \
    find test/ -type f -name "*_test.erl" | \
    xargs -I {} basename {} | \
    sed 's/_test.erl/_test/g' | \
    xargs -I {} echo "rebar3 eunit --module={}" | \
    xargs -I {} sh -c {} > "$$logfile"; \
	if grep -E "failed|syntax error" "$$logfile"; then \
		echo "Runtime Erlang tests failed (see $$(basename $$logfile))" ; \
		exit 1 ; \
	else \
		echo "Runtime Erlang tests passed" ; \
	fi

    # 1. Find all test modules
    # 2. Get the base name of the test module
    # 3. Remove the _test.erl suffix
    # 4. Echo the command to run the test module
    # 5. Execute the command

.PHONY: clean
clean:
	#
	# Clear the build
	#
	rm -rf build bin codegen-*/build codegen-*/bin *.log
	rm -rf ~/.m2/repository/io/smithy/beam

# Usage: make examples
.PHONY: examples
examples:
	mkdir -p build
	rm -rf build/*.log
	#
	# Run $(EXAMPLES_COUNT) examples in parallel
	#
	find examples/*/*/Makefile -type f -maxdepth 2 -exec dirname {} \; | xargs -S1024 -P $(EXAMPLES_COUNT) -I {} sh -c ' \
		example="{}"; \
		name="$$(basename $$example)"; \
		logfile="build/$$name.log"; \
		sleep 1; \
		echo "Running: $$example" ; \
		make $$example > $$logfile 2>&1; \
		if grep -E "make.*Error" $$logfile; then \
			echo "$$example ...failed (see $$logfile)" ; \
		else \
			echo "$$example ...ok" ; \
		fi; \
	'

# Usage: make examples/erlang/weather-service
.PHONY: $(EXAMPLES)
examples/%: $(EXAMPLES)
	#
	# Build $@
	#
	cd $@ && make clean && time make demo

# Usage: make examples/clean
.PHONY: examples/clean
examples/clean:
	#
	# Clean $(EXAMPLES)
	#
	@for x in $(EXAMPLES); do \
		echo "Cleaning: $$x" ; \
		cd $$x ; \
		make clean ; \
		echo ; \
		cd - >/dev/null; \
	done
