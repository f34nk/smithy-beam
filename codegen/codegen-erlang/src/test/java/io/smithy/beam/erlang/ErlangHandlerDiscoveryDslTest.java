package io.smithy.beam.erlang;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangHandlerDiscoveryIrTest {
  @Test
  void resolveImplAsStringMatchesGolden() throws Exception {
    DslGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryDsl.resolveImpl("basic_service_behaviour"),
        "dsl/handler_discovery_resolve_impl.expected.erl");
  }

  @Test
  void makeHandlerAsStringMatchesGolden() throws Exception {
    DslGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryDsl.makeHandler(), "dsl/handler_discovery_make_handler.expected.erl");
  }

  @Test
  void initHandlersAsStringMatchesGolden() throws Exception {
    DslGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryDsl.initHandlers(),
        "dsl/handler_discovery_init_handlers.expected.erl");
  }

  @Test
  void dispatchHandlerAsStringMatchesGolden() throws Exception {
    DslGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryDsl.dispatchHandler(),
        "dsl/handler_discovery_dispatch_handler.expected.erl");
  }

  @Test
  void operationDispatchAsStringMatchesGolden() throws Exception {
    DslGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryDsl.operationDispatch("handle_get_name"),
        "dsl/handler_discovery_handle_get_name.expected.erl");
  }
}
