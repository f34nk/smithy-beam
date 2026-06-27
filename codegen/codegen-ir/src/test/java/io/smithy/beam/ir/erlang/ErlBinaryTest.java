package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlBinaryTest {
  @Test
  void lines() {
    assertThat(new ErlBinary("name").lines()).containsExactly("<<\"name\">>");
  }

  @Test
  void asString() {
    assertThat(new ErlBinary("name").asString()).isEqualTo("<<\"name\">>");
  }
}
