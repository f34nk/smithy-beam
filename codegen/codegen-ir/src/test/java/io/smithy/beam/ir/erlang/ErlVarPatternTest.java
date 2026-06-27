package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlVarPatternTest {
  @Test
  void lines() {
    assertThat(new ErlVarPattern("Map").lines()).containsExactly("Map");
  }

  @Test
  void asString() {
    assertThat(new ErlVarPattern("Map").asString()).isEqualTo("Map");
  }
}
