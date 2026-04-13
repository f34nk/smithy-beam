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
  s3_use_path_style           = true
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    s3 = var.endpoint
  }
}

variable "endpoint" {
  type = string
}

resource "aws_s3_bucket" "config_bucket" {
  bucket = "us-east-1-nonprod-configs"
}

resource "aws_s3_object" "config_file1" {
    bucket = aws_s3_bucket.config_bucket.id
    acl    = "public-read"
    key    = "configs/config1.toml"
    source = "config1.toml"
}

resource "aws_s3_object" "config_file2" {
    bucket = aws_s3_bucket.config_bucket.id
    acl    = "public-read"
    key    = "configs/config2.toml"
    source = "config2.toml"
}
