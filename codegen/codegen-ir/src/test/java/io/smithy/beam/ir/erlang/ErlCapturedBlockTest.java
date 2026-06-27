package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlCapturedBlockTest {
    @Test
    void singleLineLines() {
        ErlCapturedBlock block = ErlCapturedBlock.capturedBlock("maps:get(Key, Map)");
        assertThat(block.lines()).containsExactly("maps:get(Key, Map)");
    }

    @Test
    void singleLineAsString() {
        ErlCapturedBlock block = ErlCapturedBlock.capturedBlock("maps:get(Key, Map)");
        assertThat(block.asString()).isEqualTo("maps:get(Key, Map)");
    }

    @Test
    void multilineLines() {
        ErlCapturedBlock block = ErlCapturedBlock.capturedBlock("line1\nline2");
        assertThat(block.lines()).containsExactly("line1", "line2");
    }

    @Test
    void multilineAsString() {
        ErlCapturedBlock block = ErlCapturedBlock.capturedBlock("line1\nline2");
        assertThat(block.asString()).isEqualTo("line1\nline2");
    }

    @Test
    void multilineLinesWithIndent() {
        ErlCapturedBlock block = ErlCapturedBlock.capturedBlock("line1\nline2");
        assertThat(block.lines(1)).containsExactly("    line1", "    line2");
    }
}
