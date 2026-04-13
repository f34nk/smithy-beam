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
    iam = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

# Create an IAM group for the demo
resource "aws_iam_group" "demo" {
  name = "iam-demo-group"
  path = "/demo/"
}

# Create an IAM user for the demo
resource "aws_iam_user" "demo" {
  name = "iam-demo-user"
  path = "/demo/"

  tags = {
    Name        = "iam-demo-user"
    Environment = "demo"
  }
}

# Add user to group
resource "aws_iam_user_group_membership" "demo" {
  user   = aws_iam_user.demo.name
  groups = [aws_iam_group.demo.name]
}

output "user_name" {
  value = aws_iam_user.demo.name
}

output "user_arn" {
  value = aws_iam_user.demo.arn
}

output "group_name" {
  value = aws_iam_group.demo.name
}
