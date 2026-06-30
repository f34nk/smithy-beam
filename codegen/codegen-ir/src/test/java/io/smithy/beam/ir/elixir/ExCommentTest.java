package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExCommentTest {
  @Test
  void singleLineLines() {
    assertThat(ExComment.comment("Generated codec.").lines()).containsExactly("# Generated codec.");
  }

  @Test
  void singleLineAsString() {
    assertThat(ExComment.comment("Generated codec.").asString()).isEqualTo("# Generated codec.");
  }

  @Test
  void multilineAsString() {
    assertThat(ExComment.comment("Line one.\n\nLine two.").asString())
        .isEqualTo("# Line one.\n#\n# Line two.");
  }

  @Test
  void emptyCommentLine() {
    assertThat(ExComment.comment("").asString()).isEqualTo("#");
  }
}
