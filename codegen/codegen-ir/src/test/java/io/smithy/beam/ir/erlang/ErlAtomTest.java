package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlAtomTest {
    @Test
    void lines() {
        assertThat(new ErlAtom("undefined").lines()).containsExactly("undefined");
    }

    @Test
    void asString() {
        assertThat(new ErlAtom("undefined").asString()).isEqualTo("undefined");
    }

    @Test
    void linesQuoted() {
        assertThat(new ErlAtom("Region").lines()).containsExactly("'Region'");
    }

    @Test
    void asStringQuoted() {
        assertThat(new ErlAtom("Region").asString()).isEqualTo("'Region'");
    }
}
