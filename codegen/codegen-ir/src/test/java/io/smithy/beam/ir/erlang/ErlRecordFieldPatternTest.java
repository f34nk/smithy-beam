package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlRecordFieldPatternTest {
  @Test
  void fieldShorthandAsString() {
    assertThat(ErlRecordFieldPattern.field("field_a").asString()).isEqualTo("field_a");
  }
}
