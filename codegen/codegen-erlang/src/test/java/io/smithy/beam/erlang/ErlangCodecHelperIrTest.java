package io.smithy.beam.erlang;

import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangCodecHelperIrTest {
  @Test
  void generateUuidAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.generateUuid(), "ir/generate_uuid.expected.erl");
  }

  @Test
  void uriEncodeAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(ErlangCodecHelperIr.uriEncode(), "ir/uri_encode.expected.erl");
  }

  @Test
  void uriDecodeAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(ErlangCodecHelperIr.uriDecode(), "ir/uri_decode.expected.erl");
  }

  @Test
  void decodeQueryParamAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.decodeQueryParam(), "ir/decode_query_param.expected.erl");
  }

  @Test
  void toBinaryRestJsonAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.toBinary(ErlangCodecHelperIr.ToBinaryVariant.REST_JSON),
        "ir/to_binary_rest_json.expected.erl");
  }

  @Test
  void encodeQueryValueRestJsonAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.encodeQueryValueRestJson(),
        "ir/encode_query_value_rest_json.expected.erl");
  }

  @Test
  void decodeListAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.decodeList(), "ir/decode_list.expected.erl");
  }

  @Test
  void decodeSparseListAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.decodeSparseList(), "ir/decode_sparse_list.expected.erl");
  }

  @Test
  void encodeSparseListAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.encodeSparseList(), "ir/encode_sparse_list.expected.erl");
  }

  @Test
  void encodeSparseMapAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.encodeSparseMap(), "ir/encode_sparse_map.expected.erl");
  }

  @Test
  void decodeJsonBodyAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.decodeJsonBody(), "ir/decode_json_body.expected.erl");
  }

  @Test
  void contentTypeMatchesAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.contentTypeMatches(), "ir/content_type_matches.expected.erl");
  }

  @Test
  void ctBaseAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(ErlangCodecHelperIr.ctBase(), "ir/ct_base.expected.erl");
  }

  @Test
  void prefixHeadersToListAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.prefixHeadersToList(), "ir/prefix_headers_to_list.expected.erl");
  }

  @Test
  void prefixHeadersFromListAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.prefixHeadersFromList(), "ir/prefix_headers_from_list.expected.erl");
  }

  @Test
  void encodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.encodeTimestampEpochSeconds(),
        "ir/encode_timestamp_epoch_seconds.expected.erl");
  }

  @Test
  void encodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.encodeTimestampDateTime(),
        "ir/encode_timestamp_date_time.expected.erl");
  }

  @Test
  void decodeTimestampEpochSecondsAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.decodeTimestampEpochSeconds(),
        "ir/decode_timestamp_epoch_seconds.expected.erl");
  }

  @Test
  void decodeTimestampDateTimeAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.decodeTimestampDateTime(),
        "ir/decode_timestamp_date_time.expected.erl");
  }

  @Test
  void headersSetAsStringMatchGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangCodecHelperIr.headersSet(), "ir/headers_set.expected.erl");
  }
}
