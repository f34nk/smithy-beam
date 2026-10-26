//// Shared client config helpers for smithy-beam Gleam clients.
////
//// This is not a second copy of gleam/http types. Generated service clients
//// keep their own ClientConfig; this module is for runtime helpers and
//// optional convenience layers.

/// Minimal HTTP client configuration used by runtime helpers.
pub type Config {
  Config(base_url: String)
}

/// Build a config from a base URL.
pub fn new(base_url: String) -> Config {
  Config(base_url:)
}

/// Read the configured base URL.
pub fn base_url(config: Config) -> String {
  config.base_url
}
