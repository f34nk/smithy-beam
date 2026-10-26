import gleam/http
import gleam/http/request
import gleam/option.{Some}
import gleeunit/should
import smithy_beam/http as beam_http

pub fn apply_base_url_sets_host_and_scheme_test() {
  let req =
    request.new()
    |> beam_http.apply_base_url("https://api.example.com:8443")

  should.equal(req.host, "api.example.com")
  should.equal(req.scheme, http.Https)
  should.equal(req.port, Some(8443))
}

pub fn set_header_if_absent_keeps_existing_test() {
  let req =
    request.new()
    |> request.set_header("content-type", "text/plain")
    |> beam_http.set_header_if_absent("content-type", "application/json")

  request.get_header(req, "content-type")
  |> should.equal(Ok("text/plain"))
}

pub fn set_header_if_absent_sets_missing_test() {
  let req =
    request.new()
    |> beam_http.set_header_if_absent("content-type", "application/json")

  request.get_header(req, "content-type")
  |> should.equal(Ok("application/json"))
}
