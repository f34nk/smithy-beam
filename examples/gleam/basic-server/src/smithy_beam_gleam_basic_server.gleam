//// Pure-router smoke for the shared basic restJson1 server modules.
////
//// Builds a synthetic gleam/http Request, runs handle_default, and prints the
//// status. Default impl stubs return not_implemented (HTTP 500). Bind
//// custom Handlers and an HTTP listener when smoking against real clients.

import basic_service_server as server
import gleam/http
import gleam/http/request
import gleam/int
import gleam/io

pub fn main() {
  let request =
    request.new()
    |> request.set_method(http.Get)
    |> request.set_path("/basic-items")
    |> request.set_body(<<>>)

  let response = server.handle_default(request)
  io.println("routed /basic-items -> status " <> int.to_string(response.status))
}
