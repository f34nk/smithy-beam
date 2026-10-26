pub fn person_to_json(
  person: Person
) -> json.Json {
  let Person(name:, age:) = person
  json.object([#("name", json.string(name)), #("age", case age {
    Some(value) -> json.int(value)
    None -> json.null()
  })])
}
