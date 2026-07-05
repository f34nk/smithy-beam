package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.Header;
import io.beam.ir.erlang.Module;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class IrGoldenAssertions {
  private IrGoldenAssertions() {}

  static void assertGolden(Function function, String resourcePath) throws IOException {
    assertThat(normalizeTrailingNewline(ErlangRenderer.renderFunction(function)))
        .isEqualTo(readExpectedString(resourcePath));
  }

  static void assertGolden(Module module, String resourcePath) throws IOException {
    assertThat(normalizeTrailingNewline(ErlangRenderer.render(module)))
        .isEqualTo(readExpectedString(resourcePath));
  }

  static void assertGolden(Header header, String resourcePath) throws IOException {
    assertThat(normalizeTrailingNewline(ErlangRenderer.render(header)))
        .isEqualTo(readExpectedString(resourcePath));
  }

  static String normalizeTrailingNewline(String text) {
    if (text.endsWith("\n")) {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }

  static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        IrGoldenAssertions.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
