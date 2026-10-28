//// Sans-io client for basic_service_client.
////
//// Each operation exposes a request builder and a response parser on gleam/http types. Callers own HTTP send.

import basic_service_types
import gleam/http/request.{type Request}
import gleam/http/response.{type Response}
import basic_service_rest_json_1
import gleam/http
import gleam/json
import gleam/option
import gleam/string
import smithy_beam/http as beam_http

/// Minimal client configuration for sans-io request builders.
pub type ClientConfig {
  ClientConfig(base_url: String)
}

/// Domain client errors from response parsing (unexpected status, decode failure, modeled service errors). Transport failures stay with the caller HTTP client.
pub type ClientError {
  NotImplemented
  UnexpectedStatus(status: Int, body: BitArray)
  DecodeError(message: String)
  ModeledError(name: String, message: String)
}

/// Returns every named type in the service closure for the given name.
///
/// ## How to call
///
/// Supply `name` as the path label. Set the `verbose` query parameter when you want
/// optional fields in the response.
///
/// ```
/// config = %{base_url: "http://localhost:8080"}
/// input  = %{name: "example", verbose: true}
/// {:ok, output} = BasicServiceClient.get_type_closure(config, input)
/// ```
///
/// Failures surface as `{:error, term()}`. A missing name may map to the modeled
/// `BasicNotFound` error.
///
/// Builds a gleam/http Request.
pub fn get_type_closure_request(
  config: ClientConfig,
  input: basic_service_types.GetTypeClosureInput
) -> Request(BitArray) {
  let ClientConfig(base_url:) = config
  let basic_service_types.GetTypeClosureInput(name:, ..) = input
  let path = "/types/{name}"
    |> string.replace("{name}", option.unwrap(name, ""))
  let body_bits = <<>>
  request.new()
  |> request.set_method(http.Get)
  |> beam_http.apply_base_url(base_url)
  |> request.set_path(path)
  |> request.set_body(body_bits)
}

/// Returns every named type in the service closure for the given name.
///
/// ## How to call
///
/// Supply `name` as the path label. Set the `verbose` query parameter when you want
/// optional fields in the response.
///
/// ```
/// config = %{base_url: "http://localhost:8080"}
/// input  = %{name: "example", verbose: true}
/// {:ok, output} = BasicServiceClient.get_type_closure(config, input)
/// ```
///
/// Failures surface as `{:error, term()}`. A missing name may map to the modeled
/// `BasicNotFound` error.
///
/// Parses a gleam/http Response.
pub fn get_type_closure_response(
  response: Response(BitArray)
) -> Result(basic_service_types.GetTypeClosureOutput, ClientError) {
  case response.status {
    200 ->
      case json.parse_bits(response.body, basic_service_rest_json_1.get_type_closure_output_decoder()) {
        Ok(out) -> Ok(out)
        Error(_) -> Error(DecodeError(message: "failed to decode response body"))
      }
    _ -> Error(UnexpectedStatus(status: response.status, body: response.body))
  }
}

/// Returns basic items across all pages.
///
/// ## How to call
///
/// Erlang:
///
/// {ok, Items} = basic_service_client:list_basic_items(Config, Input).
///
/// Elixir:
///
/// ```
/// config = %{base_url: "http://localhost:8080", http_client: MyHttpMock}
/// input = %{page_size: 10}
/// {:ok, items} = BasicServiceClient.list_basic_items(config, input)
/// ```
///
/// Builds a gleam/http Request.
pub fn list_basic_items_request(
  config: ClientConfig,
  input: basic_service_types.ListBasicItemsInput
) -> Request(BitArray) {
  let ClientConfig(base_url:) = config
  let basic_service_types.ListBasicItemsInput(..) = input
  let path = "/basic-items"
  let body_bits = <<>>
  request.new()
  |> request.set_method(http.Get)
  |> beam_http.apply_base_url(base_url)
  |> request.set_path(path)
  |> request.set_body(body_bits)
}

/// Returns basic items across all pages.
///
/// ## How to call
///
/// Erlang:
///
/// {ok, Items} = basic_service_client:list_basic_items(Config, Input).
///
/// Elixir:
///
/// ```
/// config = %{base_url: "http://localhost:8080", http_client: MyHttpMock}
/// input = %{page_size: 10}
/// {:ok, items} = BasicServiceClient.list_basic_items(config, input)
/// ```
///
/// Parses a gleam/http Response.
pub fn list_basic_items_response(
  response: Response(BitArray)
) -> Result(basic_service_types.ListBasicItemsOutput, ClientError) {
  case response.status {
    200 ->
      case json.parse_bits(response.body, basic_service_rest_json_1.list_basic_items_output_decoder()) {
        Ok(out) -> Ok(out)
        Error(_) -> Error(DecodeError(message: "failed to decode response body"))
      }
    _ -> Error(UnexpectedStatus(status: response.status, body: response.body))
  }
}
