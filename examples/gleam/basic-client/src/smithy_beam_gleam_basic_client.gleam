//// Sans-io Gleam client demo for the shared basic restJson1 model.
////
//// Builds a request, optionally sends it with httpc, and parses the response.
//// Transport failures are expected when no compatible server is listening.

import basic_service_client as client
import basic_service_types as types
import gleam/int
import gleam/io
import gleam/option.{None, Some}
import smithy_beam/httpc as beam_httpc

pub fn main() {
  let config = client.ClientConfig(base_url: "http://127.0.0.1:8080")
  let input =
    types.GetTypeClosureInput(
      name: Some("example"),
      verbose: Some(True),
      request_tag: None,
    )

  let request = client.get_type_closure_request(config, input)
  io.println("built sans-io request for get_type_closure")

  case beam_httpc.send_bits(request) {
    Ok(response) ->
      case client.get_type_closure_response(response) {
        Ok(_output) -> io.println("parsed response Ok")
        Error(client.DecodeError(message:)) ->
          io.println("decode error: " <> message)
        Error(client.UnexpectedStatus(status:, body: _)) ->
          io.println("unexpected status " <> int.to_string(status))
        Error(_) -> io.println("client error")
      }
    Error(_) ->
      io.println(
        "transport error (start a compatible restJson1 server on :8080 to smoke)",
      )
  }
}
