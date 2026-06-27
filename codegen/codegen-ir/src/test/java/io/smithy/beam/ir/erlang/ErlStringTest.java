package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlStringTest {
  @Test
  void lines() {
    assertThat(new ErlString("hello").lines()).containsExactly("\"hello\"");
  }

  @Test
  void asString() {
    assertThat(new ErlString("hello").asString()).isEqualTo("\"hello\"");
  }

  @Test
  void escapesEmbeddedQuotes() {
    assertThat(ErlString.string("say \"hi\"").asString()).isEqualTo("\"say \\\"hi\\\"\"");
  }
}
