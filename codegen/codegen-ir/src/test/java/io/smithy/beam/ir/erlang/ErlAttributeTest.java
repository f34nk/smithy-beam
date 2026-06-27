package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlAttributeTest {
  @Test
  void moduleAttributeLines() {
    assertThat(new ErlAttribute("module", "basic_service_rest_json_1").lines())
        .containsExactly("-module(basic_service_rest_json_1).");
  }

  @Test
  void moduleAttributeAsString() {
    assertThat(new ErlAttribute("module", "basic_service_rest_json_1").asString())
        .isEqualTo("-module(basic_service_rest_json_1).");
  }

  @Test
  void includeAttributeAsString() {
    assertThat(new ErlAttribute("include", "\"basic_types.hrl\"").asString())
        .isEqualTo("-include(\"basic_types.hrl\").");
  }

  @Test
  void exportAttributeAsString() {
    assertThat(new ErlAttribute("export", "[decode_basic_item/1]").asString())
        .isEqualTo("-export([decode_basic_item/1]).");
  }
}
