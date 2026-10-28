//// REST JSON 1 codecs for smithy.beam.demo.basic#BasicService (generated). Do not edit.

import basic_service_types
import gleam/dict
import gleam/dynamic
import gleam/dynamic/decode
import gleam/float
import gleam/json
import gleam/option
import gleam/time/timestamp

pub fn basic_status_to_json(
  value: basic_service_types.BasicStatus
) -> json.Json {
  case value {
    basic_service_types.Active -> json.string("ACTIVE")
    basic_service_types.Inactive -> json.string("INACTIVE")
    basic_service_types.Pending -> json.string("PENDING")
    basic_service_types.BasicStatusUnknown(value) -> json.string(value)
  }
}

pub fn basic_status_decoder() -> decode.Decoder(basic_service_types.BasicStatus) {
  decode.string
  |> decode.map(fn(wire) { case wire {
    "ACTIVE" -> basic_service_types.Active
    "INACTIVE" -> basic_service_types.Inactive
    "PENDING" -> basic_service_types.Pending
    other -> basic_service_types.BasicStatusUnknown(other)
  } })
}

pub fn json_to_basic_status(
  data: dynamic.Dynamic
) -> Result(basic_service_types.BasicStatus, List(decode.DecodeError)) {
  decode.run(data, basic_status_decoder())
}

pub fn basic_priority_to_json(
  value: basic_service_types.BasicPriority
) -> json.Json {
  case value {
    basic_service_types.Low -> json.int(1)
    basic_service_types.Medium -> json.int(2)
    basic_service_types.High -> json.int(3)
    basic_service_types.BasicPriorityUnknown(value) -> json.int(value)
  }
}

pub fn basic_priority_decoder() -> decode.Decoder(basic_service_types.BasicPriority) {
  decode.int
  |> decode.map(fn(wire) { case wire {
    1 -> basic_service_types.Low
    2 -> basic_service_types.Medium
    3 -> basic_service_types.High
    other -> basic_service_types.BasicPriorityUnknown(other)
  } })
}

pub fn json_to_basic_priority(
  data: dynamic.Dynamic
) -> Result(basic_service_types.BasicPriority, List(decode.DecodeError)) {
  decode.run(data, basic_priority_decoder())
}

pub fn basic_union_to_json(
  value: basic_service_types.BasicUnion
) -> json.Json {
  case value {
    basic_service_types.Text(inner) -> json.object([#("text", json.string(inner))])
    basic_service_types.Number(inner) -> json.object([#("number", json.int(inner))])
    basic_service_types.Flag(inner) -> json.object([#("flag", json.bool(inner))])
    basic_service_types.BasicUnionUnknown(tag) -> json.object([#(tag, json.null())])
  }
}

pub fn basic_union_decoder() -> decode.Decoder(basic_service_types.BasicUnion) {
  decode.dict(decode.string, decode.dynamic)
    |> decode.then(fn(entries) {
      case dict.to_list(entries) {
        [#("text", inner)] ->
          case decode.run(inner, decode.string) {
            Ok(v) -> decode.success(basic_service_types.Text(v))
            Error(_) -> decode.success(basic_service_types.BasicUnionUnknown("text"))
          }
        [#("number", inner)] ->
          case decode.run(inner, decode.int) {
            Ok(v) -> decode.success(basic_service_types.Number(v))
            Error(_) -> decode.success(basic_service_types.BasicUnionUnknown("number"))
          }
        [#("flag", inner)] ->
          case decode.run(inner, decode.bool) {
            Ok(v) -> decode.success(basic_service_types.Flag(v))
            Error(_) -> decode.success(basic_service_types.BasicUnionUnknown("flag"))
          }
        [#(tag, _)] -> decode.success(basic_service_types.BasicUnionUnknown(tag))
        _ -> decode.success(basic_service_types.BasicUnionUnknown(""))
      }
    })
}

pub fn json_to_basic_union(
  data: dynamic.Dynamic
) -> Result(basic_service_types.BasicUnion, List(decode.DecodeError)) {
  decode.run(data, basic_union_decoder())
}

pub fn get_type_closure_input_to_json(
  value: basic_service_types.GetTypeClosureInput
) -> json.Json {
  let basic_service_types.GetTypeClosureInput(name:, verbose:, request_tag:) = value
  json.object([#("name", json.nullable(name, json.string)), #("verbose", json.nullable(verbose, json.bool)), #("requestTag", json.nullable(request_tag, json.string))])
}

pub fn get_type_closure_input_decoder() -> decode.Decoder(basic_service_types.GetTypeClosureInput) {
  use name <- decode.optional_field("name", option.None, decode.optional(decode.string))
  use verbose <- decode.optional_field("verbose", option.None, decode.optional(decode.bool))
  use request_tag <- decode.optional_field("requestTag", option.None, decode.optional(decode.string))
  decode.success(basic_service_types.GetTypeClosureInput(name: name, verbose: verbose, request_tag: request_tag))
}

pub fn json_to_get_type_closure_input(
  data: dynamic.Dynamic
) -> Result(basic_service_types.GetTypeClosureInput, List(decode.DecodeError)) {
  decode.run(data, get_type_closure_input_decoder())
}

pub fn get_type_closure_output_to_json(
  value: basic_service_types.GetTypeClosureOutput
) -> json.Json {
  let basic_service_types.GetTypeClosureOutput(etag:, basic_string:, basic_integer:, basic_long:, basic_float:, basic_boolean:, basic_blob:, basic_byte:, basic_short:, basic_double:, basic_big_integer:, basic_timestamp:, basic_document:, basic_status:, basic_priority:, basic_list:, basic_map:, basic_union:, basic_item:) = value
  json.object([#("etag", json.nullable(etag, json.string)), #("basicString", json.nullable(basic_string, json.string)), #("basicInteger", json.nullable(basic_integer, json.int)), #("basicLong", json.nullable(basic_long, json.int)), #("basicFloat", json.nullable(basic_float, json.float)), #("basicBoolean", json.nullable(basic_boolean, json.bool)), #("basicBlob", json.nullable(basic_blob, fn(_) { json.string("") })), #("basicByte", json.nullable(basic_byte, json.int)), #("basicShort", json.nullable(basic_short, json.int)), #("basicDouble", json.nullable(basic_double, json.float)), #("basicBigInteger", json.nullable(basic_big_integer, json.int)), #("basicTimestamp", json.nullable(basic_timestamp, fn(ts) { json.int(float.truncate(timestamp.to_unix_seconds(ts))) })), #("basicDocument", json.nullable(basic_document, fn(_) { json.null() })), #("basicStatus", json.nullable(basic_status, basic_status_to_json)), #("basicPriority", json.nullable(basic_priority, basic_priority_to_json)), #("basicList", json.nullable(basic_list, fn(items) { json.array(items, json.string) })), #("basicMap", json.nullable(basic_map, fn(d) { json.dict(d, fn(k) { k }, json.string) })), #("basicUnion", json.nullable(basic_union, basic_union_to_json)), #("basicItem", json.nullable(basic_item, basic_item_to_json))])
}

pub fn get_type_closure_output_decoder() -> decode.Decoder(basic_service_types.GetTypeClosureOutput) {
  use etag <- decode.optional_field("etag", option.None, decode.optional(decode.string))
  use basic_string <- decode.optional_field("basicString", option.None, decode.optional(decode.string))
  use basic_integer <- decode.optional_field("basicInteger", option.None, decode.optional(decode.int))
  use basic_long <- decode.optional_field("basicLong", option.None, decode.optional(decode.int))
  use basic_float <- decode.optional_field("basicFloat", option.None, decode.optional(decode.float))
  use basic_boolean <- decode.optional_field("basicBoolean", option.None, decode.optional(decode.bool))
  use basic_blob <- decode.optional_field("basicBlob", option.None, decode.optional(decode.bit_array))
  use basic_byte <- decode.optional_field("basicByte", option.None, decode.optional(decode.int))
  use basic_short <- decode.optional_field("basicShort", option.None, decode.optional(decode.int))
  use basic_double <- decode.optional_field("basicDouble", option.None, decode.optional(decode.float))
  use basic_big_integer <- decode.optional_field("basicBigInteger", option.None, decode.optional(decode.int))
  use basic_timestamp <- decode.optional_field("basicTimestamp", option.None, decode.optional(decode.int |> decode.map(timestamp.from_unix_seconds)))
  use basic_document <- decode.optional_field("basicDocument", option.None, decode.optional(decode.new_primitive_decoder("Document", fn(d) { Ok(d) })))
  use basic_status <- decode.optional_field("basicStatus", option.None, decode.optional(basic_status_decoder()))
  use basic_priority <- decode.optional_field("basicPriority", option.None, decode.optional(basic_priority_decoder()))
  use basic_list <- decode.optional_field("basicList", option.None, decode.optional(decode.list(decode.string)))
  use basic_map <- decode.optional_field("basicMap", option.None, decode.optional(decode.dict(decode.string, decode.string)))
  use basic_union <- decode.optional_field("basicUnion", option.None, decode.optional(basic_union_decoder()))
  use basic_item <- decode.optional_field("basicItem", option.None, decode.optional(basic_item_decoder()))
  decode.success(basic_service_types.GetTypeClosureOutput(etag: etag, basic_string: basic_string, basic_integer: basic_integer, basic_long: basic_long, basic_float: basic_float, basic_boolean: basic_boolean, basic_blob: basic_blob, basic_byte: basic_byte, basic_short: basic_short, basic_double: basic_double, basic_big_integer: basic_big_integer, basic_timestamp: basic_timestamp, basic_document: basic_document, basic_status: basic_status, basic_priority: basic_priority, basic_list: basic_list, basic_map: basic_map, basic_union: basic_union, basic_item: basic_item))
}

pub fn json_to_get_type_closure_output(
  data: dynamic.Dynamic
) -> Result(basic_service_types.GetTypeClosureOutput, List(decode.DecodeError)) {
  decode.run(data, get_type_closure_output_decoder())
}

pub fn list_basic_items_input_to_json(
  value: basic_service_types.ListBasicItemsInput
) -> json.Json {
  let basic_service_types.ListBasicItemsInput(next_token:, page_size:) = value
  json.object([#("nextToken", json.nullable(next_token, json.string)), #("pageSize", json.nullable(page_size, json.int))])
}

pub fn list_basic_items_input_decoder() -> decode.Decoder(basic_service_types.ListBasicItemsInput) {
  use next_token <- decode.optional_field("nextToken", option.None, decode.optional(decode.string))
  use page_size <- decode.optional_field("pageSize", option.None, decode.optional(decode.int))
  decode.success(basic_service_types.ListBasicItemsInput(next_token: next_token, page_size: page_size))
}

pub fn json_to_list_basic_items_input(
  data: dynamic.Dynamic
) -> Result(basic_service_types.ListBasicItemsInput, List(decode.DecodeError)) {
  decode.run(data, list_basic_items_input_decoder())
}

pub fn list_basic_items_output_to_json(
  value: basic_service_types.ListBasicItemsOutput
) -> json.Json {
  let basic_service_types.ListBasicItemsOutput(items:, next_token:) = value
  json.object([#("items", json.nullable(items, fn(items) { json.array(items, basic_item_to_json) })), #("nextToken", json.nullable(next_token, json.string))])
}

pub fn list_basic_items_output_decoder() -> decode.Decoder(basic_service_types.ListBasicItemsOutput) {
  use items <- decode.optional_field("items", option.None, decode.optional(decode.list(basic_item_decoder())))
  use next_token <- decode.optional_field("nextToken", option.None, decode.optional(decode.string))
  decode.success(basic_service_types.ListBasicItemsOutput(items: items, next_token: next_token))
}

pub fn json_to_list_basic_items_output(
  data: dynamic.Dynamic
) -> Result(basic_service_types.ListBasicItemsOutput, List(decode.DecodeError)) {
  decode.run(data, list_basic_items_output_decoder())
}

pub fn basic_item_to_json(
  value: basic_service_types.BasicItem
) -> json.Json {
  let basic_service_types.BasicItem(name:, count:) = value
  json.object([#("name", json.string(name)), #("count", json.nullable(count, json.int))])
}

pub fn basic_item_decoder() -> decode.Decoder(basic_service_types.BasicItem) {
  use name <- decode.field("name", decode.string)
  use count <- decode.optional_field("count", option.None, decode.optional(decode.int))
  decode.success(basic_service_types.BasicItem(name: name, count: count))
}

pub fn json_to_basic_item(
  data: dynamic.Dynamic
) -> Result(basic_service_types.BasicItem, List(decode.DecodeError)) {
  decode.run(data, basic_item_decoder())
}
