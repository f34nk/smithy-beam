package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ErlangRuntimeTypesIrTest {

  @Test
  void runtimeTypesHeaderMatchesResource() throws IOException {
    String expected = Files.readString(runtimeHttpTypesHeader(), StandardCharsets.UTF_8);
    assertThat(
            ErlangRenderer.render(
                ErlangRuntimeTypesIr.runtimeTypesHeader("http_types", Optional.empty())))
        .isEqualTo(expected);
  }

  private static Path runtimeHttpTypesHeader() {
    Path dir = Path.of(System.getProperty("user.dir"));
    while (dir != null) {
      Path header = dir.resolve("runtime/erlang/include/http_types.hrl");
      if (Files.isRegularFile(header)) {
        return header;
      }
      header = dir.resolve("runtime/erlang/src/http_types.hrl");
      dir = dir.getParent();
    }
    throw new IllegalStateException("Could not find runtime/erlang/include/http_types.hrl");
  }
}
