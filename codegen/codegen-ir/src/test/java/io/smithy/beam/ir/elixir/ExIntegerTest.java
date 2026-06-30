package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExIntegerTest {
  @Test
  void lines() {
    assertThat(ExInteger.integer(200).lines()).containsExactly("200");
  }

  @Test
  void asString() {
    assertThat(ExInteger.integer(200).asString()).isEqualTo("200");
  }
}
