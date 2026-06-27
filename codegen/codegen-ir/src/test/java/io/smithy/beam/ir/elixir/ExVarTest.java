package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExVarTest {
  @Test
  void lines() {
    assertThat(ExVar.var("map").lines()).containsExactly("map");
  }

  @Test
  void asString() {
    assertThat(ExVar.var("map").asString()).isEqualTo("map");
  }
}
