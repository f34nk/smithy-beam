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
    String expected = runtimeHttpTypesHeader();
    assertThat(
            ErlangRenderer.render(
                ErlangRuntimeTypesIr.runtimeTypesHeader("runtime_types", Optional.empty())))
        .isEqualTo(expected);
  }

  private static String runtimeHttpTypesHeader() throws IOException {
    String resourcePath = "runtime/erlang/src/runtime_types.hrl";
    try (InputStream in =
        ErlangRuntimeTypesIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource: " + resourcePath);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
