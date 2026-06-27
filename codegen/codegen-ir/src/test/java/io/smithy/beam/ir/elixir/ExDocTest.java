package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExDocTest {
  @Test
  void singleLineAsString() {
    assertThat(ExDoc.doc("Decode a BasicItem from a JSON map.").asString())
        .isEqualTo("@doc \"Decode a BasicItem from a JSON map.\"");
  }

  @Test
  void multilineAsString() {
    assertThat(ExDoc.doc("Line one.\n\nLine two.").asString())
        .isEqualTo("@doc \"\"\"\n  Line one.\n\n  Line two.\n\"\"\"");
  }
}
