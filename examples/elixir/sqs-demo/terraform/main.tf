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
    sqs = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

# Create a standard SQS queue for the demo
resource "aws_sqs_queue" "demo" {
  name                       = "sqs-demo-queue"
  delay_seconds              = 0
  max_message_size           = 262144
  message_retention_seconds  = 345600
  receive_wait_time_seconds  = 0
  visibility_timeout_seconds = 30

  tags = {
    Name        = "sqs-demo-queue"
    Environment = "demo"
  }
}

# Create a dead letter queue
resource "aws_sqs_queue" "dlq" {
  name = "sqs-demo-dlq"

  tags = {
    Name        = "sqs-demo-dlq"
    Environment = "demo"
  }
}

output "queue_url" {
  value = aws_sqs_queue.demo.url
}

output "queue_arn" {
  value = aws_sqs_queue.demo.arn
}

output "dlq_url" {
  value = aws_sqs_queue.dlq.url
}
