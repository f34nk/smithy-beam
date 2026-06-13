.PHONY: all
all: clean build

.PHONY: build
build:
	# 
	# Build
	#

	# EC2 uses ec2Query, but Smithy Python 0.3.0 only supports awsQuery for client codegen — so the client build can't succeed. Switching the default build to type codegen (which already works).
	# smithy build
	
	# build type only
	smithy build --config smithy-build-types.json

	tree build/
	mkdir -p generated
	#
	# Copy the generated files to the src/generated directory
	#
	cp -r build/smithy/*/python-*-codegen/* generated/
	tree generated

# .PHONY: demo
# demo: build

.PHONY: demo
demo:
	#
	# TODO: temporarily disabled - python generation is very slow
	#

.PHONY: clean
clean:
	rm -rf build generated
