package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlCommentTest {
    @Test
    void singleLineLines() {
        assertThat(ErlComment.comment("Generated codec.").lines())
                .containsExactly("%% Generated codec.");
    }

    @Test
    void singleLineAsString() {
        assertThat(ErlComment.comment("Generated codec.").asString())
                .isEqualTo("%% Generated codec.");
    }

    @Test
    void multilineAsString() {
        assertThat(ErlComment.comment("Line one.\n\nLine two.").asString())
                .isEqualTo("%% Line one.\n%%\n%% Line two.");
    }

    @Test
    void emptyCommentLine() {
        assertThat(ErlComment.comment("").asString()).isEqualTo("%%");
    }
}
