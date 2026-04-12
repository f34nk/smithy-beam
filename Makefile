X:=$(shell find examples -type d -not -name examples -maxdepth 1 -exec basename {} \;)
EXAMPLES:=$(foreach x,$(X),examples/$(x)/)
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
test: test/java test/resources

.PHONY: test/java
test/java:
	#
	# Run JAVA tests
	#
	rm -rf test-errors.log
	./gradlew test 2>test-errors.log
	[ -s test-errors.log ] || rm -rf test-errors.log

.PHONY: test/resources
test/resources:
	#
	# Run resources tests
	#
	temp="$$(pwd)/build/tmp" && \
	rm -rf "$$temp" && \
	mkdir -p "$$temp/test" && \
	find runtime-erlang/*/* -type f -name *.erl -exec cp {} "$$temp/test/" \; && \
	echo \
	{erl_opts, [debug_info]}.\\n\
	{deps, []}.\\n\
	{eunit_opts, [verbose]}. > "$$temp/rebar.config" && \
    tree $$temp && \
    cd "$$temp" && \
    find test/ -type f -name "*_test.erl" | \
    xargs -I {} basename {} | \
    sed 's/_test.erl/_test/g' | \
    xargs -I {} echo "rebar3 eunit --module={}" | \
    xargs -I {} sh -c {}

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
	rm -rf build codegen-*/build bin test-errors.log build-errors.log
	rm -rf ~/.m2/repository/io/smithy/beam

# Usage: make examples
.PHONY: examples
examples: examples/clean
	mkdir -p build
	rm -rf build/*.log
	#
	# Run $(EXAMPLES_COUNT) examples in parallel
	#
	find examples -type d -not -name examples -maxdepth 1 -exec basename {} \; | xargs -S1024 -P $(EXAMPLES_COUNT) -I {} sh -c ' \
		example="{}"; \
		logfile="build/$$example.log"; \
		sleep 1; \
		echo "Running: $$example" ; \
		make examples/$$example > $$logfile 2>&1; \
		if grep -q "make.*Error" $$logfile; then \
			echo "$$example ...failed (see $$logfile)" ; \
		else \
			echo "$$example ...ok" ; \
		fi; \
	'

# Usage: make examples/user-service
.PHONY: $(EXAMPLES)
examples/%: $(EXAMPLES)
	#
	# Build $@
	#
	cd $@ && make clean && time make demo; \
	make docker/stop

# Usage: make examples/clean
.PHONY: examples/clean
examples/clean:
	#
	# Build $(EXAMPLES)
	#
	@for x in $(EXAMPLES); do \
		cd $$x ; \
		make clean ; \
		cd - ; \
	done
