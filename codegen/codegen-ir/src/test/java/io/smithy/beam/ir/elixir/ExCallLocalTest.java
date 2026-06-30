package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExCallLocalTest {
  @Test
  void localCallLines() {
    ExCallLocal call = ExCallLocal.callLocal("is_map", ExVar.var("map"));
    assertThat(call.lines()).containsExactly("is_map(map)");
  }

  @Test
  void localCallAsString() {
    ExCallLocal call = ExCallLocal.callLocal("is_map", ExVar.var("map"));
    assertThat(call.asString()).isEqualTo("is_map(map)");
  }
}
