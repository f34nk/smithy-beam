//// URL and header helpers that build or tweak gleam/http values.

import gleam/http.{Http, Https}
import gleam/http/request.{type Request}
import gleam/int
import gleam/option.{type Option, None, Some}
import gleam/string
import smithy_beam/utils

/// Apply scheme, host, and port from a base URL onto a request.
pub fn apply_base_url(req: Request(body), base_url: String) -> Request(body) {
  let #(scheme_prefix, authority) = utils.split_base_url(base_url)
  case authority {
    "" -> req
    _ -> {
      let req = case scheme_prefix {
        "https://" -> request.set_scheme(req, Https)
        "http://" -> request.set_scheme(req, Http)
        _ -> req
      }
      let #(host, port) = split_authority(authority)
      let req = request.set_host(req, host)
      case port {
        Some(p) -> request.set_port(req, p)
        None -> req
      }
    }
  }
}

/// Set a header when the key is not already present (case-insensitive).
pub fn set_header_if_absent(
  req: Request(body),
  key: String,
  value: String,
) -> Request(body) {
  case request.get_header(req, key) {
    Ok(_) -> req
    Error(_) -> request.set_header(req, key, value)
  }
}

fn split_authority(authority: String) -> #(String, Option(Int)) {
  case string.split_once(authority, on: ":") {
    Ok(#(host, port_str)) ->
      case int.parse(port_str) {
        Ok(port) -> #(host, Some(port))
        Error(_) -> #(authority, None)
      }
    Error(_) -> #(authority, None)
  }
}
