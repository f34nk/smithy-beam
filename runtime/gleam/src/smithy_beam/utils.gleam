//// Small shared helpers for smithy-beam Gleam runtime modules.

import gleam/int
import gleam/option.{type Option, None, Some}
import gleam/uri

/// Split a base URL into a scheme prefix (https://) and authority
/// (host or host:port). Empty or unparsable input returns #("", base_url).
pub fn split_base_url(base_url: String) -> #(String, String) {
  case base_url {
    "" -> #("", "")
    _ ->
      case uri.parse(base_url) {
        Ok(parsed) ->
          case parsed.host {
            Some(host) -> {
              let scheme_prefix = case parsed.scheme {
                Some(scheme) -> scheme <> "://"
                None -> ""
              }
              let authority = host_with_port(parsed.scheme, host, parsed.port)
              #(scheme_prefix, authority)
            }
            None -> #("", base_url)
          }
        Error(_) -> #("", base_url)
      }
  }
}

/// Resolve an HTTP host from an optional base URL, or from AWS-style
/// endpoint prefix + region when no base URL is set.
pub fn endpoint_host(
  base_url: Option(String),
  endpoint_prefix: Option(String),
  region: String,
) -> Option(String) {
  case base_url {
    Some(url) -> {
      let #(_, authority) = split_base_url(url)
      case authority {
        "" -> None
        host -> Some(host)
      }
    }
    None ->
      case endpoint_prefix {
        Some(prefix) -> Some(prefix <> "." <> region <> ".amazonaws.com")
        None -> None
      }
  }
}

fn host_with_port(
  scheme: Option(String),
  host: String,
  port: Option(Int),
) -> String {
  case port {
    None -> host
    Some(p) ->
      case is_default_port(scheme, p) {
        True -> host
        False -> host <> ":" <> int.to_string(p)
      }
  }
}

fn is_default_port(scheme: Option(String), port: Int) -> Bool {
  case scheme {
    Some("https") -> port == 443
    Some("http") -> port == 80
    _ -> False
  }
}
