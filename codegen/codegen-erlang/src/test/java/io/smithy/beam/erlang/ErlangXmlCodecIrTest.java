package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangXmlCodecIrTest {
  @Test
  void decodeSparseMapAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangXmlCodecIr.decodeSparseMap(), "ir/decode_sparse_map.expected.erl");
  }

  @Test
  void restXmlHelpersAsStringMatchGolden() throws IOException {
    String decode = IrGoldenAssertions.renderFunctions(ErlangXmlCodecIr.restXmlDecodeHelpers());
    String encode = IrGoldenAssertions.renderFunctions(ErlangXmlCodecIr.restXmlEncodeHelpers());
    assertThat(IrGoldenAssertions.normalizeTrailingNewline(decode + "\n" + encode))
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/rest_xml_helpers.expected.erl"));
  }
}
