//// Type definitions for the basic model.

import gleam/dict.{type Dict}
import gleam/option.{type Option}

/// A named string shape.
pub type BasicString = String

pub type Status {
  Active
  Inactive
  Unknown(String)
}

pub type Person {
  Person(name: String, age: Option(Int))
}
