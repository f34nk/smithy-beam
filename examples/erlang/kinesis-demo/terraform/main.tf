terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      # https://github.com/hashicorp/terraform-provider-aws/issues/45292
      version = "= 6.22.0"
    }
  }
}

provider "aws" {
  region                      = "us-east-1"
  access_key                  = "dummy"
  secret_key                  = "dummy"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    kinesis = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

# Create a Kinesis data stream for the demo
resource "aws_kinesis_stream" "demo" {
  name             = "kinesis-demo-stream"
  shard_count      = 1
  retention_period = 24

  stream_mode_details {
    stream_mode = "PROVISIONED"
  }

  tags = {
    Name        = "kinesis-demo-stream"
    Environment = "demo"
  }
}

output "stream_name" {
  value = aws_kinesis_stream.demo.name
}

output "stream_arn" {
  value = aws_kinesis_stream.demo.arn
}
