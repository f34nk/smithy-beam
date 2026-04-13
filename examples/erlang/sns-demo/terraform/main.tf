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
    sns = var.endpoint
    sqs = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

# Create an SNS topic for the demo
resource "aws_sns_topic" "demo" {
  name = "sns-demo-topic"

  tags = {
    Name        = "sns-demo-topic"
    Environment = "demo"
    Project     = "smithy-erlang"
  }
}

# Create an SQS queue to receive SNS messages
resource "aws_sqs_queue" "demo" {
  name                       = "sns-demo-subscriber-queue"
  message_retention_seconds  = 345600
  visibility_timeout_seconds = 30

  tags = {
    Name        = "sns-demo-subscriber-queue"
    Environment = "demo"
  }
}

# Subscribe the SQS queue to the SNS topic
resource "aws_sns_topic_subscription" "demo" {
  topic_arn = aws_sns_topic.demo.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.demo.arn
}

output "topic_arn" {
  value = aws_sns_topic.demo.arn
}

output "topic_name" {
  value = aws_sns_topic.demo.name
}

output "queue_url" {
  value = aws_sqs_queue.demo.url
}

output "subscription_arn" {
  value = aws_sns_topic_subscription.demo.arn
}
