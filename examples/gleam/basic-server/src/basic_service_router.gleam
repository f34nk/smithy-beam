//// Pure restJson1 router for smithy.beam.demo.basic#BasicService.
////
//// Matches method/path, decodes input, calls handlers, and encodes Response values. Generated; do not edit.

import basic_service_types
import basic_service_behaviour
import basic_service_rest_json_1
import gleam/http/request.{type Request}
import gleam/http/response.{type Response}
import gleam/http
import gleam/bit_array
import gleam/dict
import gleam/json
import gleam/list
import gleam/option
import gleam/string
import gleam/uri
import gleam/int

/// Operation handlers supplied by the application.
pub type Handlers {
  Handlers(handle_get_type_closure: basic_service_behaviour.HandleGetTypeClosure, handle_list_basic_items: basic_service_behaviour.HandleListBasicItems)
}

/// Route a request to a matching operation handler.
pub fn route(
  request: Request(BitArray),
  handlers: Handlers
) -> Response(BitArray) {
  case try_get_type_closure(request, handlers) {
    Ok(response) -> response
    Error(_) ->
    case try_list_basic_items(request, handlers) {
      Ok(response) -> response
      Error(_) ->
        not_found(request)
    }
  }
}

fn try_get_type_closure(
  request: Request(BitArray),
  handlers: Handlers
) -> Result(Response(BitArray), Nil) {
  case request.method {
    http.Get ->
      case parse_labels(request.path, "/types/{name}") {
        Ok(labels) -> {
          let input = decode_get_type_closure_input(request, labels)
          let Handlers(handle_get_type_closure:, ..) = handlers
          Ok(finish_handler(handle_get_type_closure(input), 200, encode_get_type_closure_output))
        }
        Error(_) -> Error(Nil)
      }
    _ -> Error(Nil)
  }
}

fn decode_get_type_closure_input(
  request: Request(BitArray),
  labels: dict.Dict(String, String)
) -> basic_service_types.GetTypeClosureInput {
  basic_service_types.GetTypeClosureInput(name: case dict.get(labels, "name") { Ok(v) -> option.Some(v) Error(_) -> option.None }, verbose: query_bool(request, "verbose"), request_tag: header_string(request, "x-request-tag"))
}

fn encode_get_type_closure_output(
  output: basic_service_types.GetTypeClosureOutput
) -> BitArray {
  bit_array.from_string(json.to_string(basic_service_rest_json_1.get_type_closure_output_to_json(output)))
}

fn try_list_basic_items(
  request: Request(BitArray),
  handlers: Handlers
) -> Result(Response(BitArray), Nil) {
  case request.method {
    http.Get ->
      case request.path {
        "/basic-items" -> {
          let input = decode_list_basic_items_input(request, dict.new())
          let Handlers(handle_list_basic_items:, ..) = handlers
          Ok(finish_handler(handle_list_basic_items(input), 200, encode_list_basic_items_output))
        }
        _ -> Error(Nil)
      }
    _ -> Error(Nil)
  }
}

fn decode_list_basic_items_input(
  request: Request(BitArray),
  _labels: dict.Dict(String, String)
) -> basic_service_types.ListBasicItemsInput {
  basic_service_types.ListBasicItemsInput(next_token: query_string(request, "nextToken"), page_size: query_int(request, "pageSize"))
}

fn encode_list_basic_items_output(
  output: basic_service_types.ListBasicItemsOutput
) -> BitArray {
  bit_array.from_string(json.to_string(basic_service_rest_json_1.list_basic_items_output_to_json(output)))
}

fn not_found(
  _request: Request(BitArray)
) -> Response(BitArray) {
  response.new(404)
  |> response.set_body(bit_array.from_string("not_found"))
}

fn finish_handler(
  result: Result(a, String),
  status: Int,
  encode: fn(a) -> BitArray
) -> Response(BitArray) {
  case result {
    Ok(output) -> success_response(status, encode(output))
    Error(message) ->
      response.new(500)
      |> response.set_body(bit_array.from_string(message))
  }
}

fn success_response(
  status: Int,
  body: BitArray
) -> Response(BitArray) {
  response.new(status)
  |> response.set_header("content-type", "application/json")
  |> response.set_body(body)
}

fn query_params(
  request: Request(BitArray)
) -> List(#(String, String)) {
  case request.query {
    option.Some(q) ->
      case uri.parse_query(q) {
        Ok(params) -> params
        Error(_) -> []
      }
    option.None -> []
  }
}

fn query_string(
  request: Request(BitArray),
  name: String
) -> option.Option(String) {
  case list.key_find(query_params(request), name) {
    Ok(value) -> option.Some(value)
    Error(_) -> option.None
  }
}

fn query_bool(
  request: Request(BitArray),
  name: String
) -> option.Option(Bool) {
  case query_string(request, name) {
    option.Some("true") -> option.Some(True)
    option.Some("false") -> option.Some(False)
    _ -> option.None
  }
}

fn query_int(
  request: Request(BitArray),
  name: String
) -> option.Option(Int) {
  case query_string(request, name) {
    option.Some(raw) ->
      case int.parse(raw) {
        Ok(n) -> option.Some(n)
        Error(_) -> option.None
      }
    option.None -> option.None
  }
}

fn header_string(
  request: Request(BitArray),
  name: String
) -> option.Option(String) {
  case list.key_find(request.headers, string.lowercase(name)) {
    Ok(value) -> option.Some(value)
    Error(_) -> option.None
  }
}

fn parse_labels(
  path: String,
  template: String
) -> Result(dict.Dict(String, String), Nil) {
  case match_segments(segments(path), segments(template), dict.new()) {
    Ok(labels) -> Ok(labels)
    Error(_) -> Error(Nil)
  }
}

fn segments(
  path: String
) -> List(String) {
  string.split(path, "/")
  |> list.filter(fn(seg) { seg != "" })
}

fn match_segments(
  path_segs: List(String),
  tpl_segs: List(String),
  acc: dict.Dict(String, String)
) -> Result(dict.Dict(String, String), Nil) {
  case path_segs, tpl_segs {
    [], [] -> Ok(acc)
    [seg, ..rest_path], [tpl_seg, ..rest_tpl] ->
      case label_name(tpl_seg) {
        Ok(key) -> {
          let val = case uri.percent_decode(seg) {
            Ok(decoded) -> decoded
            Error(_) -> seg
          }
          match_segments(rest_path, rest_tpl, dict.insert(acc, key, val))
        }
        Error(_) if seg == tpl_seg ->
          match_segments(rest_path, rest_tpl, acc)
        Error(_) -> Error(Nil)
      }
    _, _ -> Error(Nil)
  }
}

fn label_name(
  tpl_seg: String
) -> Result(String, Nil) {
  case string.starts_with(tpl_seg, "{") && string.ends_with(tpl_seg, "}") {
    True -> {
      let len = string.length(tpl_seg)
      Ok(string.slice(tpl_seg, at_index: 1, length: len - 2))
    }
    False -> Error(Nil)
  }
}
