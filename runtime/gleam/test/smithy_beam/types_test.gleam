import gleeunit/should
import smithy_beam/types

pub fn new_config_test() {
  let config = types.new("https://example.com")
  types.base_url(config)
  |> should.equal("https://example.com")
}
