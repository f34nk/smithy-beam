import gleam/option.{None, Some}
import gleeunit/should
import smithy_beam/utils

pub fn split_base_url_empty_test() {
  utils.split_base_url("")
  |> should.equal(#("", ""))
}

pub fn split_base_url_https_test() {
  utils.split_base_url("https://api.example.com")
  |> should.equal(#("https://", "api.example.com"))
}

pub fn split_base_url_with_port_test() {
  utils.split_base_url("https://localhost:4566")
  |> should.equal(#("https://", "localhost:4566"))
}

pub fn split_base_url_default_https_port_test() {
  utils.split_base_url("https://api.example.com:443")
  |> should.equal(#("https://", "api.example.com"))
}

pub fn split_base_url_unparsed_test() {
  utils.split_base_url("not-a-url")
  |> should.equal(#("", "not-a-url"))
}

pub fn endpoint_host_from_prefix_test() {
  utils.endpoint_host(None, Some("s3"), "us-west-2")
  |> should.equal(Some("s3.us-west-2.amazonaws.com"))
}

pub fn endpoint_host_from_base_url_test() {
  utils.endpoint_host(Some("https://custom.example:8443"), None, "us-east-1")
  |> should.equal(Some("custom.example:8443"))
}

pub fn endpoint_host_missing_prefix_test() {
  utils.endpoint_host(None, None, "us-east-1")
  |> should.equal(None)
}
