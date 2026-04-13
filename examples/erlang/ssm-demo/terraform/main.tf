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
    ssm = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

# Create some SSM parameters for the demo
resource "aws_ssm_parameter" "database_host" {
  name  = "/demo/database/host"
  type  = "String"
  value = "localhost"

  tags = {
    Environment = "demo"
    Project     = "smithy-erlang"
  }
}

resource "aws_ssm_parameter" "database_port" {
  name  = "/demo/database/port"
  type  = "String"
  value = "5432"

  tags = {
    Environment = "demo"
    Project     = "smithy-erlang"
  }
}

resource "aws_ssm_parameter" "database_name" {
  name  = "/demo/database/name"
  type  = "String"
  value = "myapp_db"

  tags = {
    Environment = "demo"
    Project     = "smithy-erlang"
  }
}

resource "aws_ssm_parameter" "api_key" {
  name  = "/demo/api/key"
  type  = "SecureString"
  value = "super-secret-api-key-12345"

  tags = {
    Environment = "demo"
    Project     = "smithy-erlang"
    Sensitive   = "true"
  }
}

output "database_host_name" {
  value = aws_ssm_parameter.database_host.name
}

output "database_port_name" {
  value = aws_ssm_parameter.database_port.name
}

output "api_key_name" {
  value = aws_ssm_parameter.api_key.name
}
