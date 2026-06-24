package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlNilPatternTest {
    @Test
    void nilPatternLines() {
        assertThat(ErlNilPattern.nilPattern().lines()).containsExactly("[]");
    }

    @Test
    void nilPatternAsString() {
        assertThat(ErlNilPattern.nilPattern().asString()).isEqualTo("[]");
    }
}
