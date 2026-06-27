package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlIntegerPatternTest {
  @Test
  void lines() {
    assertThat(new ErlIntegerPattern(0).lines()).containsExactly("0");
  }

  @Test
  void asString() {
    assertThat(new ErlIntegerPattern(0).asString()).isEqualTo("0");
  }
}
