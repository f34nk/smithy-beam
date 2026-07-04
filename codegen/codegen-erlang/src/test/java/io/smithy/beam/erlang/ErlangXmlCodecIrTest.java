package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.erlang.ErlFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangXmlCodecIrTest {
  @Test
  void decodeSparseMapLinesMatchGolden() {
    ErlFunction fn = ErlangXmlCodecIr.decodeSparseMap();
    assertThat(fn.lines())
        .containsExactly(
            "decode_sparse_map(undefined) -> undefined;",
            """
                decode_sparse_map(Map) when is_map(Map) -> maps:map(fun
                    (_K, null) ->
                        undefined;
                    (_K, V) ->
                        V
                end, Map).""");
  }

  @Test
  void restXmlHelpersAsStringMatchGolden() throws IOException {
    String decode =
        ErlangXmlCodecIr.restXmlDecodeHelpers().stream()
            .map(ErlFunction::asString)
            .collect(java.util.stream.Collectors.joining("\n\n"));
    String encode =
        ErlangXmlCodecIr.restXmlEncodeHelpers().stream()
            .map(ErlFunction::asString)
            .collect(java.util.stream.Collectors.joining("\n\n"));
    String combined = decode + "\n\n" + encode;
    assertThat(combined).isEqualTo(readExpectedString("ir/rest_xml_helpers.expected.erl"));
  }

  @Test
  void decodeSparseMapAsStringMatchGolden() throws IOException {
    ErlFunction fn = ErlangXmlCodecIr.decodeSparseMap();
    assertThat(fn.asString()).isEqualTo(readExpectedString("ir/decode_sparse_map.expected.erl"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangXmlCodecIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
