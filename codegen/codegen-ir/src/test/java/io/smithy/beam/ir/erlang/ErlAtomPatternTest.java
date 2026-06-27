package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlAtomPatternTest {
    @Test
    void lines() {
        assertThat(new ErlAtomPattern("undefined").lines()).containsExactly("undefined");
    }

    @Test
    void asString() {
        assertThat(new ErlAtomPattern("undefined").asString()).isEqualTo("undefined");
    }
}
