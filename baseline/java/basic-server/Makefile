.PHONY: all
all: clean build

.PHONY: build
build:
	# 
	# Build
	#
	smithy build
	tree build/
	mkdir -p src/generated
	#
	# Copy the generated files to the src/generated directory
	#
	cp -r build/smithy/server/java-codegen/* src/generated/
	tree src/generated

.PHONY: demo
demo: build

.PHONY: clean
clean:
	rm -rf build src/generated
