package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.ErlangRenderer;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.Header;
import io.beam.dsl.erlang.Module;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

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

  static void assertGoldenFunctions(List<Function> functions, String resourcePath)
      throws IOException {
    assertThat(normalizeTrailingNewline(renderFunctions(functions)))
        .isEqualTo(readExpectedString(resourcePath));
  }

  /** Renders functions with the same spacing as {@link ErlangRenderer#render(Module)}. */
  static String renderFunctions(List<Function> functions) {
    return functions.stream().map(ErlangRenderer::renderFunction).collect(Collectors.joining("\n"));
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
