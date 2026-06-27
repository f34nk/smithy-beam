package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlVarTest {
  @Test
  void lines() {
    assertThat(new ErlVar("Map").lines()).containsExactly("Map");
  }

  @Test
  void asString() {
    assertThat(new ErlVar("Map").asString()).isEqualTo("Map");
  }
}
