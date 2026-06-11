.PHONY: all
all: clean build

.PHONY: build
build:
	# 
	# Build
	#
	smithy build
	tree build/
	mkdir -p generated
	#
	# Copy the generated files to the src/generated directory
	#
	cp -r build/smithy/client/python-*-codegen/* generated
	tree generated

.PHONY: demo
demo: build

.PHONY: clean
clean:
	rm -rf build generated
