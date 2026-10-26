pub fn json_to_person(
  data: dynamic.Dynamic
) -> Result(Person, List(decode.DecodeError)) {
  use name <- result.try(decode.field(data, "name", decode.string))
  use age <- result.try(decode.optional_field(data, "age", decode.int))
  Ok(Person(name: name, age: age))
}
