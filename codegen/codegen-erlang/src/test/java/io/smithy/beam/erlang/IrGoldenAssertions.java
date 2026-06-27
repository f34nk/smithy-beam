package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.erlang.IrObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

final class IrGoldenAssertions {
  private IrGoldenAssertions() {}

  static void assertLinesAndAsString(IrObject ir, String resourcePath) throws IOException {
    List<String> expectedLines = readExpectedLines(resourcePath);
    String expectedString = readExpectedString(resourcePath);
    assertThat(ir.lines()).isEqualTo(expectedLines);
    assertThat(ir.asString()).isEqualTo(expectedString);
    assertThat(ir.asString()).isEqualTo(String.join("\n", expectedLines));
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
