//// Thin HTTP adapter for smithy.beam.demo.basic#BasicService.
////
//// Calls the pure router; does not own a listen loop or import HTTP frameworks.

import basic_service_router
import basic_service_impl
import gleam/http/request.{type Request}
import gleam/http/response.{type Response}

pub type ServerConfig {
  ServerConfig(port: Int)
}

/// Build Handlers from the generated impl module stubs.
///
/// Override individual fields when wiring custom handlers.
pub fn default_handlers() -> basic_service_router.Handlers {
  basic_service_router.Handlers(
    handle_get_type_closure: basic_service_impl.handle_get_type_closure,
    handle_list_basic_items: basic_service_impl.handle_list_basic_items
  )
}

/// Feed a request into the pure router and return the Response.
///
/// Bind this from your HTTP server loop.
pub fn handle(
  request: Request(BitArray),
  handlers: basic_service_router.Handlers
) -> Response(BitArray) {
  basic_service_router.route(request, handlers)
}

/// Convenience: handle using default_handlers/0.
pub fn handle_default(
  request: Request(BitArray)
) -> Response(BitArray) {
  handle(request, default_handlers())
}

/// No embedded listener. Call handle/2 from your HTTP stack adapter.
pub fn start(
  _config: ServerConfig
) -> Result(Nil, String) {
  Error("use handle/2 with your HTTP stack; no embedded listener")
}
