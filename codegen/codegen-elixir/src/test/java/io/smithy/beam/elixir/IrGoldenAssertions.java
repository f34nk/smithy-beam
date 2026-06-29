package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.elixir.IrObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

final class IrGoldenAssertions {
  private IrGoldenAssertions() {}

  static void assertLinesAndAsString(IrObject ir, String resourcePath) throws IOException {
    String expectedString = readExpectedString(resourcePath);
    assertThat(ir.asString()).isEqualTo(expectedString);
    List<String> expectedLines = Arrays.asList(expectedString.split("\n", -1));
    List<String> actualLines = Arrays.asList(ir.asString().split("\n", -1));
    assertThat(actualLines).isEqualTo(expectedLines);
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

  static List<String> readExpectedLines(String resourcePath) throws IOException {
    return Arrays.asList(readExpectedString(resourcePath).split("\n", -1));
  }
}
