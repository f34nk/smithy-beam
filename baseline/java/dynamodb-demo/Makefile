.PHONY: all
all: clean copy build

.PHONY: copy
copy:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/dynamodb/service/2012-08-10/dynamodb-2012-08-10.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/dynamodb/service/2012-08-10/dynamodb-2012-08-10.json
	mkdir -p model
	mv dynamodb-2012-08-10.json model/

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
	cp -r build/smithy/client/java-codegen/* generated/
	tree generated

.PHONY: demo
demo: copy build

.PHONY: clean
clean:
	rm -rf build generated model
