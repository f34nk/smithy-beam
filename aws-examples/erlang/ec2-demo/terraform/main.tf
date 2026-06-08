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
    ec2 = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

# Create a VPC for the demo
resource "aws_vpc" "demo" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "ec2-demo-vpc"
  }
}

# Create a subnet in the VPC
resource "aws_subnet" "demo" {
  vpc_id                  = aws_vpc.demo.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true

  tags = {
    Name = "ec2-demo-subnet"
  }
}

# Create a security group
resource "aws_security_group" "demo" {
  name        = "ec2-demo-sg"
  description = "Security group for EC2 demo"
  vpc_id      = aws_vpc.demo.id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "ec2-demo-sg"
  }
}

output "vpc_id" {
  value = aws_vpc.demo.id
}

output "subnet_id" {
  value = aws_subnet.demo.id
}

output "security_group_id" {
  value = aws_security_group.demo.id
}
