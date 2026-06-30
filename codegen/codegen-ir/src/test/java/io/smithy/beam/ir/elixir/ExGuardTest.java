package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExGuardTest {
  @Test
  void lines() {
    assertThat(ExGuard.guard("is_map", ExVar.var("map")).lines()).containsExactly("is_map(map)");
  }

  @Test
  void asString() {
    assertThat(ExGuard.guard("is_map", ExVar.var("map")).asString()).isEqualTo("is_map(map)");
  }

  @Test
  void exprGuardAsString() {
    assertThat(ExGuard.exprGuard(ExVar.var("map")).asString()).isEqualTo("map");
  }
}
