package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Function;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirCodecHelperIrTest {
  @Test
  void generateUuidIsStructural() {
    assertStructural(ElixirCodecHelperDsl.generateUuid());
  }

  @Test
  void uriEncodeIsStructural() {
    assertStructural(ElixirCodecHelperDsl.uriEncode());
  }

  @Test
  void uriDecodeIsStructural() {
    assertStructural(ElixirCodecHelperDsl.uriDecode());
  }

  @Test
  void decodeQueryParamIsStructural() {
    assertStructural(ElixirCodecHelperDsl.decodeQueryParam());
  }

  @Test
  void toBinaryRestJsonIsStructural() {
    assertStructural(ElixirCodecHelperDsl.toBinary(ElixirCodecHelperDsl.ToBinaryVariant.REST_JSON));
  }

  @Test
  void toBinaryXmlQueryIsStructural() {
    assertStructural(ElixirCodecHelperDsl.toBinary(ElixirCodecHelperDsl.ToBinaryVariant.XML_QUERY));
  }

  @Test
  void encodeQueryValueRestJsonIsStructural() {
    assertStructural(ElixirCodecHelperDsl.encodeQueryValueRestJson());
  }

  @Test
  void encodeQueryValueXmlQueryIsStructural() {
    assertStructural(ElixirCodecHelperDsl.encodeQueryValueXmlQuery());
  }

  @Test
  void decodeListIsStructural() {
    assertStructural(ElixirCodecHelperDsl.decodeList());
  }

  @Test
  void decodeSparseListIsStructural() {
    assertStructural(ElixirCodecHelperDsl.decodeSparseList());
  }

  @Test
  void encodeSparseListIsStructural() {
    assertStructural(ElixirCodecHelperDsl.encodeSparseList());
  }

  @Test
  void encodeSparseMapIsStructural() {
    assertStructural(ElixirCodecHelperDsl.encodeSparseMap());
  }

  @Test
  void decodeJsonBodyIsStructural() {
    assertStructural(ElixirCodecHelperDsl.decodeJsonBody());
  }

  @Test
  void contentTypeMatchesIsStructural() {
    assertStructural(ElixirCodecHelperDsl.contentTypeMatches());
  }

  @Test
  void ctBaseIsStructural() {
    assertStructural(ElixirCodecHelperDsl.ctBase());
  }

  @Test
  void prefixHeadersToListIsStructural() {
    assertStructural(ElixirCodecHelperDsl.prefixHeadersToList());
  }

  @Test
  void prefixHeadersFromListIsStructural() {
    assertStructural(ElixirCodecHelperDsl.prefixHeadersFromList());
  }

  @Test
  void encodeTimestampEpochSecondsIsStructural() {
    assertStructural(ElixirCodecHelperDsl.encodeTimestampEpochSeconds());
  }

  @Test
  void encodeTimestampDateTimeIsStructural() {
    assertStructural(ElixirCodecHelperDsl.encodeTimestampDateTime());
  }

  @Test
  void decodeTimestampEpochSecondsIsStructural() {
    assertStructural(ElixirCodecHelperDsl.decodeTimestampEpochSeconds());
  }

  @Test
  void decodeTimestampDateTimeIsStructural() {
    assertStructural(ElixirCodecHelperDsl.decodeTimestampDateTime());
  }

  @Test
  void headersSetIsStructural() {
    assertStructural(ElixirCodecHelperDsl.headersSet());
  }

  private static void assertStructural(List<Function> functions) {
    for (Function fn : functions) {
      ElixirDslTestSupport.assertStructural(fn);
    }
  }
}
