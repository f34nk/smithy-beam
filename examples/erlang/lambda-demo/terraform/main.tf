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
    lambda = var.endpoint
    iam    = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

# IAM role for Lambda execution
resource "aws_iam_role" "lambda_role" {
  name = "lambda-demo-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Create a simple Python Lambda function
# The zip file contains a simple handler
data "archive_file" "lambda_zip" {
  type        = "zip"
  output_path = "${path.module}/lambda_function.zip"

  source {
    content  = <<EOF
def handler(event, context):
    return {
        'statusCode': 200,
        'body': 'Hello from Lambda!'
    }
EOF
    filename = "lambda_function.py"
  }
}

resource "aws_lambda_function" "demo" {
  filename         = data.archive_file.lambda_zip.output_path
  function_name    = "lambda-demo-function"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambda_function.handler"
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  runtime          = "python3.9"

  tags = {
    Name        = "lambda-demo-function"
    Environment = "demo"
    Project     = "smithy-erlang"
  }
}

output "function_name" {
  value = aws_lambda_function.demo.function_name
}

output "function_arn" {
  value = aws_lambda_function.demo.arn
}
