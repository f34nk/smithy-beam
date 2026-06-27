package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlNilPatternTest {
  @Test
  void nilPatternLines() {
    assertThat(ErlNilPattern.nilPattern().lines()).containsExactly("[]");
  }

  @Test
  void nilPatternAsString() {
    assertThat(ErlNilPattern.nilPattern().asString()).isEqualTo("[]");
  }
}
