package io.smithy.beam.erlang;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangHandlerDiscoveryIrTest {
  @Test
  void resolveImplAsStringMatchesGolden() throws Exception {
    IrGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryIr.resolveImpl("basic_service_behaviour"),
        "ir/handler_discovery_resolve_impl.expected.erl");
  }

  @Test
  void makeHandlerAsStringMatchesGolden() throws Exception {
    IrGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryIr.makeHandler(), "ir/handler_discovery_make_handler.expected.erl");
  }

  @Test
  void initHandlersAsStringMatchesGolden() throws Exception {
    IrGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryIr.initHandlers(), "ir/handler_discovery_init_handlers.expected.erl");
  }

  @Test
  void dispatchHandlerAsStringMatchesGolden() throws Exception {
    IrGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryIr.dispatchHandler(),
        "ir/handler_discovery_dispatch_handler.expected.erl");
  }

  @Test
  void operationDispatchAsStringMatchesGolden() throws Exception {
    IrGoldenAssertions.assertGolden(
        ErlangHandlerDiscoveryIr.operationDispatch("handle_get_name"),
        "ir/handler_discovery_handle_get_name.expected.erl");
  }
}
