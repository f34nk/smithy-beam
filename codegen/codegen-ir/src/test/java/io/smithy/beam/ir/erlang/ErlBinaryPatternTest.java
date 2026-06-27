package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlBinaryPatternTest {
  @Test
  void binaryPatternLines() {
    assertThat(ErlBinaryPattern.binaryPattern("payload").lines())
        .containsExactly("<<\"payload\">>");
  }

  @Test
  void binaryPatternAsString() {
    assertThat(ErlBinaryPattern.binaryPattern("payload").asString()).isEqualTo("<<\"payload\">>");
  }
}
