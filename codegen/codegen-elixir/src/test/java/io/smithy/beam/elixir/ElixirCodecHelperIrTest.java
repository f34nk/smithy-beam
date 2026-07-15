package io.smithy.beam.elixir;

import io.beam.ir.elixir.Function;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirCodecHelperIrTest {
  @Test
  void generateUuidIsStructural() {
    assertStructural(ElixirCodecHelperIr.generateUuid());
  }

  @Test
  void uriEncodeIsStructural() {
    assertStructural(ElixirCodecHelperIr.uriEncode());
  }

  @Test
  void uriDecodeIsStructural() {
    assertStructural(ElixirCodecHelperIr.uriDecode());
  }

  @Test
  void decodeQueryParamIsStructural() {
    assertStructural(ElixirCodecHelperIr.decodeQueryParam());
  }

  @Test
  void toBinaryRestJsonIsStructural() {
    assertStructural(ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.REST_JSON));
  }

  @Test
  void toBinaryXmlQueryIsStructural() {
    assertStructural(ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.XML_QUERY));
  }

  @Test
  void encodeQueryValueRestJsonIsStructural() {
    assertStructural(ElixirCodecHelperIr.encodeQueryValueRestJson());
  }

  @Test
  void encodeQueryValueXmlQueryIsStructural() {
    assertStructural(ElixirCodecHelperIr.encodeQueryValueXmlQuery());
  }

  @Test
  void decodeListIsStructural() {
    assertStructural(ElixirCodecHelperIr.decodeList());
  }

  @Test
  void decodeSparseListIsStructural() {
    assertStructural(ElixirCodecHelperIr.decodeSparseList());
  }

  @Test
  void encodeSparseListIsStructural() {
    assertStructural(ElixirCodecHelperIr.encodeSparseList());
  }

  @Test
  void encodeSparseMapIsStructural() {
    assertStructural(ElixirCodecHelperIr.encodeSparseMap());
  }

  @Test
  void decodeJsonBodyIsStructural() {
    assertStructural(ElixirCodecHelperIr.decodeJsonBody());
  }

  @Test
  void contentTypeMatchesIsStructural() {
    assertStructural(ElixirCodecHelperIr.contentTypeMatches());
  }

  @Test
  void ctBaseIsStructural() {
    assertStructural(ElixirCodecHelperIr.ctBase());
  }

  @Test
  void prefixHeadersToListIsStructural() {
    assertStructural(ElixirCodecHelperIr.prefixHeadersToList());
  }

  @Test
  void prefixHeadersFromListIsStructural() {
    assertStructural(ElixirCodecHelperIr.prefixHeadersFromList());
  }

  @Test
  void encodeTimestampEpochSecondsIsStructural() {
    assertStructural(ElixirCodecHelperIr.encodeTimestampEpochSeconds());
  }

  @Test
  void encodeTimestampDateTimeIsStructural() {
    assertStructural(ElixirCodecHelperIr.encodeTimestampDateTime());
  }

  @Test
  void decodeTimestampEpochSecondsIsStructural() {
    assertStructural(ElixirCodecHelperIr.decodeTimestampEpochSeconds());
  }

  @Test
  void decodeTimestampDateTimeIsStructural() {
    assertStructural(ElixirCodecHelperIr.decodeTimestampDateTime());
  }

  @Test
  void headersSetIsStructural() {
    assertStructural(ElixirCodecHelperIr.headersSet());
  }

  private static void assertStructural(List<Function> functions) {
    for (Function fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }
}
