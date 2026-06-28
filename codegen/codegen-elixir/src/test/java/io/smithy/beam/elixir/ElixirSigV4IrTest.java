package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ElixirSigV4IrTest {
  @Test
  void signMatchesGolden() throws IOException {
    assertThat(ElixirSigV4Ir.sign().asString())
        .isEqualTo(readExpectedString("ir/sigv4_sign.expected.ex"));
  }

  @Test
  void presignMatchesGolden() throws IOException {
    assertThat(ElixirSigV4Ir.presign().asString())
        .isEqualTo(readExpectedString("ir/sigv4_presign.expected.ex"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirSigV4IrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
