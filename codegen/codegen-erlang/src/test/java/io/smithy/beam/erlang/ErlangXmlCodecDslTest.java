package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangXmlCodecIrTest {
  @Test
  void decodeSparseMapAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangXmlCodecDsl.decodeSparseMap(), "dsl/decode_sparse_map.expected.erl");
  }

  @Test
  void restXmlHelpersAsStringMatchGolden() throws IOException {
    String decode = DslGoldenAssertions.renderFunctions(ErlangXmlCodecDsl.restXmlDecodeHelpers());
    String encode = DslGoldenAssertions.renderFunctions(ErlangXmlCodecDsl.restXmlEncodeHelpers());
    assertThat(DslGoldenAssertions.normalizeTrailingNewline(decode + "\n" + encode))
        .isEqualTo(DslGoldenAssertions.readExpectedString("dsl/rest_xml_helpers.expected.erl"));
  }
}
