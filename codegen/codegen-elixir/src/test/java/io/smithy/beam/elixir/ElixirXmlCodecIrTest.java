package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ElixirXmlCodecIrTest {
  @Test
  void decodeSparseMapAsStringMatchGolden() throws IOException {
    ExFunction fn = ElixirCodecHelperIr.decodeSparseMap();
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString()).isEqualTo(readExpectedString("ir/decode_sparse_map.expected.ex"));
  }

  @Test
  void elementTextAsStringMatchGolden() throws IOException {
    ExFunction fn =
        ElixirXmlCodecIr.restXmlDecodeHelpers().stream()
            .filter(function -> function.name().equals("element_text"))
            .findFirst()
            .orElseThrow();
    assertGolden(fn, "ir/xml_codec_element_text.expected.ex");
  }

  @Test
  void xmlChildListAsStringMatchGolden() throws IOException {
    ExFunction fn = ElixirXmlCodecIr.xmlChildList();
    assertGolden(fn, "ir/xml_child_list.expected.ex");
  }

  @Test
  void restXmlDecodeHelpersAreStructural() {
    for (ExFunction fn : ElixirXmlCodecIr.restXmlDecodeHelpers()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  private static void assertGolden(ExFunction fn, String resourcePath) throws IOException {
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString()).isEqualTo(readExpectedString(resourcePath));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirXmlCodecIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
