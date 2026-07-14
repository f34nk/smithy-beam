package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.Module;
import io.beam.ir.elixir.TypesModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class IrGoldenAssertions {
  private IrGoldenAssertions() {}

  static void assertGolden(Function function, String resourcePath) throws IOException {
    assertThat(ElixirRenderer.renderFunction(function)).isEqualTo(readExpectedString(resourcePath));
  }

  static void assertGolden(Module module, String resourcePath) throws IOException {
    assertThat(ElixirRenderer.render(module)).isEqualTo(readExpectedString(resourcePath));
  }

  static void assertGolden(TypesModule typesModule, String resourcePath) throws IOException {
    assertThat(ElixirRenderer.render(typesModule)).isEqualTo(readExpectedString(resourcePath));
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
