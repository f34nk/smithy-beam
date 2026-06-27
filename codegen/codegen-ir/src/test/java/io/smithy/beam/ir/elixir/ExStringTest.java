package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExStringTest {
  @Test
  void lines() {
    assertThat(ExString.string("name").lines()).containsExactly("\"name\"");
  }

  @Test
  void asString() {
    assertThat(ExString.string("name").asString()).isEqualTo("\"name\"");
  }

  @Test
  void asStringEscapesQuotes() {
    assertThat(ExString.string("say \"hi\"").asString()).isEqualTo("\"say \\\"hi\\\"\"");
  }
}
