package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlExportAttributeTest {
  @Test
  void emptyExportLines() {
    assertThat(ErlExportAttribute.export(List.of()).lines()).containsExactly("-export([]).");
  }

  @Test
  void emptyExportAsString() {
    assertThat(ErlExportAttribute.export(List.of()).asString()).isEqualTo("-export([]).");
  }

  @Test
  void shortExportAsString() {
    assertThat(ErlExportAttribute.export(List.of("decode_basic_item/1")).asString())
        .isEqualTo("-export([decode_basic_item/1]).");
  }

  @Test
  void exportBreaksLongExportListLines() {
    assertThat(
            ErlExportAttribute.export(
                    List.of(
                        "encode_get_type_closure_request/1",
                        "decode_get_type_closure_request/2",
                        "decode_get_type_closure_response/1",
                        "encode_get_type_closure_response/1",
                        "decode_get_type_closure_response_error/3"))
                .lines())
        .containsExactly(
            "-export([",
            "    encode_get_type_closure_request/1,",
            "    decode_get_type_closure_request/2,",
            "    decode_get_type_closure_response/1,",
            "    encode_get_type_closure_response/1,",
            "    decode_get_type_closure_response_error/3",
            "]).");
  }

  @Test
  void exportBreaksLongExportListAsString() {
    String out =
        ErlExportAttribute.export(
                List.of(
                    "encode_get_type_closure_request/1",
                    "decode_get_type_closure_request/2",
                    "decode_get_type_closure_response/1",
                    "encode_get_type_closure_response/1",
                    "decode_get_type_closure_response_error/3"))
            .asString();

    assertThat(out).contains("-export([");
    assertThat(out).contains("    encode_get_type_closure_request/1,");
    assertThat(out).contains("    decode_get_type_closure_response_error/3");
    assertThat(out).contains("]).");
    assertThat(out)
        .isEqualTo(
            String.join(
                "\n",
                ErlExportAttribute.export(
                        List.of(
                            "encode_get_type_closure_request/1",
                            "decode_get_type_closure_request/2",
                            "decode_get_type_closure_response/1",
                            "encode_get_type_closure_response/1",
                            "decode_get_type_closure_response_error/3"))
                    .lines()));
  }
}
