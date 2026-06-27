package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExNilTest {
  @Test
  void lines() {
    assertThat(ExNil.nil().lines()).containsExactly("nil");
  }

  @Test
  void asString() {
    assertThat(ExNil.nil().asString()).isEqualTo("nil");
  }
}
