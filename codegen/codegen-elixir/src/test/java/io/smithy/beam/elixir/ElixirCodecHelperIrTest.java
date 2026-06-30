package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ElixirCodecHelperIrTest {
  @Test
  void generateUuidAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.generateUuid(), "ir/generate_uuid.expected.ex");
  }

  @Test
  void uriEncodeAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.uriEncode(), "ir/uri_encode.expected.ex");
  }

  @Test
  void uriDecodeAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.uriDecode(), "ir/uri_decode.expected.ex");
  }

  @Test
  void decodeQueryParamAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.decodeQueryParam(), "ir/decode_query_param.expected.ex");
  }

  @Test
  void toBinaryRestJsonAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.REST_JSON),
        "ir/to_binary_rest_json.expected.ex");
  }

  @Test
  void toBinaryXmlQueryAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.XML_QUERY),
        "ir/to_binary_xml_query.expected.ex");
  }

  @Test
  void encodeQueryValueRestJsonAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.encodeQueryValueRestJson(),
        "ir/encode_query_value_rest_json.expected.ex");
  }

  @Test
  void encodeQueryValueXmlQueryAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.encodeQueryValueXmlQuery(),
        "ir/encode_query_value_xml_query.expected.ex");
  }

  @Test
  void decodeListAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.decodeList(), "ir/decode_list.expected.ex");
  }

  @Test
  void decodeSparseListAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.decodeSparseList(), "ir/decode_sparse_list.expected.ex");
  }

  @Test
  void encodeSparseListAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.encodeSparseList(), "ir/encode_sparse_list.expected.ex");
  }

  @Test
  void encodeSparseMapAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.encodeSparseMap(), "ir/encode_sparse_map.expected.ex");
  }

  @Test
  void decodeJsonBodyAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.decodeJsonBody(), "ir/decode_json_body.expected.ex");
  }

  @Test
  void contentTypeMatchesAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.contentTypeMatches(), "ir/content_type_matches.expected.ex");
  }

  @Test
  void ctBaseAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.ctBase(), "ir/ct_base.expected.ex");
  }

  @Test
  void prefixHeadersToListAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.prefixHeadersToList(), "ir/prefix_headers_to_list.expected.ex");
  }

  @Test
  void prefixHeadersFromListAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.prefixHeadersFromList(), "ir/prefix_headers_from_list.expected.ex");
  }

  @Test
  void encodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.encodeTimestampEpochSeconds(),
        "ir/encode_timestamp_epoch_seconds.expected.ex");
  }

  @Test
  void encodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.encodeTimestampDateTime(), "ir/encode_timestamp_date_time.expected.ex");
  }

  @Test
  void decodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.decodeTimestampEpochSeconds(),
        "ir/decode_timestamp_epoch_seconds.expected.ex");
  }

  @Test
  void decodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirCodecHelperIr.decodeTimestampDateTime(), "ir/decode_timestamp_date_time.expected.ex");
  }

  @Test
  void headersSetAsStringMatchGolden() throws IOException {
    assertGolden(ElixirCodecHelperIr.headersSet(), "ir/headers_set.expected.ex");
  }

  private static void assertGolden(ExFunction fn, String resourcePath) throws IOException {
    ElixirIrTestSupport.assertStructural(fn);
    assertThat(fn.asString()).isEqualTo(readExpectedString(resourcePath));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirCodecHelperIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        return "";
      }
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
