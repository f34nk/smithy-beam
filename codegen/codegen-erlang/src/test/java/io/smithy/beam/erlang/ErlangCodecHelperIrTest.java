package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.erlang.ErlFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangCodecHelperIrTest {
  @Test
  void generateUuidAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.generateUuid(), "ir/generate_uuid.expected.erl");
  }

  @Test
  void uriEncodeAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.uriEncode(), "ir/uri_encode.expected.erl");
  }

  @Test
  void uriDecodeAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.uriDecode(), "ir/uri_decode.expected.erl");
  }

  @Test
  void decodeQueryParamAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.decodeQueryParam(), "ir/decode_query_param.expected.erl");
  }

  @Test
  void toBinaryRestJsonAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.toBinary(ErlangCodecHelperIr.ToBinaryVariant.REST_JSON),
        "ir/to_binary_rest_json.expected.erl");
  }

  @Test
  void encodeQueryValueRestJsonAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.encodeQueryValueRestJson(),
        "ir/encode_query_value_rest_json.expected.erl");
  }

  @Test
  void decodeListAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.decodeList(), "ir/decode_list.expected.erl");
  }

  @Test
  void decodeSparseListAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.decodeSparseList(), "ir/decode_sparse_list.expected.erl");
  }

  @Test
  void encodeSparseListAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.encodeSparseList(), "ir/encode_sparse_list.expected.erl");
  }

  @Test
  void encodeSparseMapAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.encodeSparseMap(), "ir/encode_sparse_map.expected.erl");
  }

  @Test
  void decodeJsonBodyAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.decodeJsonBody(), "ir/decode_json_body.expected.erl");
  }

  @Test
  void contentTypeMatchesAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.contentTypeMatches(), "ir/content_type_matches.expected.erl");
  }

  @Test
  void ctBaseAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.ctBase(), "ir/ct_base.expected.erl");
  }

  @Test
  void prefixHeadersToListAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.prefixHeadersToList(), "ir/prefix_headers_to_list.expected.erl");
  }

  @Test
  void prefixHeadersFromListAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.prefixHeadersFromList(), "ir/prefix_headers_from_list.expected.erl");
  }

  @Test
  void encodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.encodeTimestampEpochSeconds(),
        "ir/encode_timestamp_epoch_seconds.expected.erl");
  }

  @Test
  void encodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.encodeTimestampDateTime(),
        "ir/encode_timestamp_date_time.expected.erl");
  }

  @Test
  void decodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.decodeTimestampEpochSeconds(),
        "ir/decode_timestamp_epoch_seconds.expected.erl");
  }

  @Test
  void decodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangCodecHelperIr.decodeTimestampDateTime(),
        "ir/decode_timestamp_date_time.expected.erl");
  }

  @Test
  void headersSetAsStringMatchGolden() throws IOException {
    assertGolden(ErlangCodecHelperIr.headersSet(), "ir/headers_set.expected.erl");
  }

  private static void assertGolden(ErlFunction fn, String resourcePath) throws IOException {
    assertThat(fn.asString()).isEqualTo(readExpectedString(resourcePath));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangCodecHelperIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
