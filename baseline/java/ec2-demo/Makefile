.PHONY: all
all: clean copy build

.PHONY: copy
copy:
	#
	# Download model from AWS SDKs: https://github.com/aws/api-models-aws/blob/main/models/ec2/service/2016-11-15/ec2-2016-11-15.json
	#
	wget https://raw.githubusercontent.com/aws/api-models-aws/refs/heads/main/models/ec2/service/2016-11-15/ec2-2016-11-15.json
	mkdir -p model/
	mv ec2-2016-11-15.json model/

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
