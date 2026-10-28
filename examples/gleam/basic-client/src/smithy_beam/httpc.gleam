//// Optional httpc send convenience.
////
//// Generated operation pairs do not depend on this module. Callers may
//// compose request builders with send_bits and response parsers themselves.

import gleam/http/request.{type Request}
import gleam/http/response.{type Response}
import gleam/httpc

/// Send a binary-bodied HTTP request with the default httpc configuration.
pub fn send_bits(
  req: Request(BitArray),
) -> Result(Response(BitArray), httpc.HttpError) {
  httpc.send_bits(req)
}

/// Send a string-bodied HTTP request with the default httpc configuration.
pub fn send(req: Request(String)) -> Result(Response(String), httpc.HttpError) {
  httpc.send(req)
}
