package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ElixirHandlerDiscoveryIrTest {
  @Test
  void resolveImplAsStringMatchesGolden() throws IOException {
    assertThat(ElixirHandlerDiscoveryIr.resolveImpl("BasicServiceBehaviour").asString())
        .isEqualTo(readExpectedString("ir/handler_discovery_resolve_impl.expected.ex"));
  }

  @Test
  void initHandlersAsStringMatchesGolden() throws IOException {
    assertThat(ElixirHandlerDiscoveryIr.initHandlers().asString())
        .isEqualTo(readExpectedString("ir/handler_discovery_init_handlers.expected.ex"));
  }

  @Test
  void dispatchHandlerAsStringMatchesGolden() throws IOException {
    assertThat(ElixirHandlerDiscoveryIr.dispatchHandler().asString())
        .isEqualTo(readExpectedString("ir/handler_discovery_dispatch_handler.expected.ex"));
  }

  @Test
  void operationDispatchAsStringMatchesGolden() throws IOException {
    assertThat(ElixirHandlerDiscoveryIr.operationDispatch("handle_get_name").asString())
        .isEqualTo(readExpectedString("ir/handler_discovery_handle_get_name.expected.ex"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirHandlerDiscoveryIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
