//// Type definitions for the basic_service_types model.

import gleam/time/timestamp
import gleam/dynamic
import gleam/dict.{type Dict}
import gleam/option.{type Option}

pub type BasicBlob = BitArray

pub type BasicBoolean = Bool

pub type BasicString = String

pub type BasicByte = Int

pub type BasicShort = Int

pub type BasicInteger = Int

pub type BasicLong = Int

pub type BasicFloat = Float

pub type BasicDouble = Float

pub type BasicBigInteger = Int

pub type BasicTimestamp = timestamp.Timestamp

pub type BasicDocument = dynamic.Dynamic

pub type BasicItemList = List(BasicItem)

pub type BasicList = List(BasicString)

pub type BasicMap = Dict(BasicString, BasicString)

/// Item returned in list responses.
pub type BasicItem {
  BasicItem(name: BasicString, count: Option(BasicInteger))
}

pub type BasicNotFound {
  BasicNotFound(message: Option(String))
}

pub type BasicPriority {
  Low
  Medium
  High
  BasicPriorityUnknown(Int)
}

pub type GetTypeClosureInput {
  GetTypeClosureInput(name: Option(BasicString), verbose: Option(BasicBoolean), request_tag: Option(BasicString))
}

/// Lifecycle state of a basic resource.
pub type BasicStatus {
  Active
  Inactive
  Pending
  BasicStatusUnknown(String)
}

pub type BasicUnion {
  Text(BasicString)
  Number(BasicInteger)
  Flag(BasicBoolean)
  BasicUnionUnknown(String)
}

pub type GetTypeClosureOutput {
  GetTypeClosureOutput(etag: Option(BasicString), basic_string: Option(BasicString), basic_integer: Option(BasicInteger), basic_long: Option(BasicLong), basic_float: Option(BasicFloat), basic_boolean: Option(BasicBoolean), basic_blob: Option(BasicBlob), basic_byte: Option(BasicByte), basic_short: Option(BasicShort), basic_double: Option(BasicDouble), basic_big_integer: Option(BasicBigInteger), basic_timestamp: Option(BasicTimestamp), basic_document: Option(BasicDocument), basic_status: Option(BasicStatus), basic_priority: Option(BasicPriority), basic_list: Option(BasicList), basic_map: Option(BasicMap), basic_union: Option(BasicUnion), basic_item: Option(BasicItem))
}

pub type ListBasicItemsInput {
  ListBasicItemsInput(next_token: Option(BasicString), page_size: Option(BasicInteger))
}

pub type ListBasicItemsOutput {
  ListBasicItemsOutput(items: Option(BasicItemList), next_token: Option(BasicString))
}
