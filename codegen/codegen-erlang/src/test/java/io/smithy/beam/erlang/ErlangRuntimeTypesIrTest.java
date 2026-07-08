package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ErlangRuntimeTypesIrTest {

  @Test
  void runtimeTypesHeaderMatchesResource() throws IOException {
    String expected = loadResource("runtime_types.hrl");
    assertThat(
            ErlangRenderer.render(
                ErlangRuntimeTypesIr.runtimeTypesHeader("runtime_types", Optional.empty())))
        .isEqualTo(expected);
  }

  private static String loadResource(String name) throws IOException {
    try (InputStream in = ErlangRuntimeTypesIrTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("resource %s", name).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

}
