package io.smithy.beam.erlang;

import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangCodecHelperIrTest {
  @Test
  void generateUuidAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.generateUuid(), "dsl/generate_uuid.expected.erl");
  }

  @Test
  void uriEncodeAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.uriEncode(), "dsl/uri_encode.expected.erl");
  }

  @Test
  void uriDecodeAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.uriDecode(), "dsl/uri_decode.expected.erl");
  }

  @Test
  void decodeQueryParamAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.decodeQueryParam(), "dsl/decode_query_param.expected.erl");
  }

  @Test
  void toBinaryRestJsonAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.toBinary(ErlangCodecHelperDsl.ToBinaryVariant.REST_JSON),
        "dsl/to_binary_rest_json.expected.erl");
  }

  @Test
  void encodeQueryValueRestJsonAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.encodeQueryValueRestJson(),
        "dsl/encode_query_value_rest_json.expected.erl");
  }

  @Test
  void decodeListAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.decodeList(), "dsl/decode_list.expected.erl");
  }

  @Test
  void decodeSparseListAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.decodeSparseList(), "dsl/decode_sparse_list.expected.erl");
  }

  @Test
  void encodeSparseListAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.encodeSparseList(), "dsl/encode_sparse_list.expected.erl");
  }

  @Test
  void encodeSparseMapAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.encodeSparseMap(), "dsl/encode_sparse_map.expected.erl");
  }

  @Test
  void decodeJsonBodyAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.decodeJsonBody(), "dsl/decode_json_body.expected.erl");
  }

  @Test
  void contentTypeMatchesAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.contentTypeMatches(), "dsl/content_type_matches.expected.erl");
  }

  @Test
  void ctBaseAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(ErlangCodecHelperDsl.ctBase(), "dsl/ct_base.expected.erl");
  }

  @Test
  void prefixHeadersToListAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.prefixHeadersToList(), "dsl/prefix_headers_to_list.expected.erl");
  }

  @Test
  void prefixHeadersFromListAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.prefixHeadersFromList(), "dsl/prefix_headers_from_list.expected.erl");
  }

  @Test
  void encodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.encodeTimestampEpochSeconds(),
        "dsl/encode_timestamp_epoch_seconds.expected.erl");
  }

  @Test
  void encodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.encodeTimestampDateTime(),
        "dsl/encode_timestamp_date_time.expected.erl");
  }

  @Test
  void decodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.decodeTimestampEpochSeconds(),
        "dsl/decode_timestamp_epoch_seconds.expected.erl");
  }

  @Test
  void decodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.decodeTimestampDateTime(),
        "dsl/decode_timestamp_date_time.expected.erl");
  }

  @Test
  void headersSetAsStringMatchGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangCodecHelperDsl.headersSet(), "dsl/headers_set.expected.erl");
  }
}
