package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlVarPatternTest {
    @Test
    void lines() {
        assertThat(new ErlVarPattern("Map").lines()).containsExactly("Map");
    }

    @Test
    void asString() {
        assertThat(new ErlVarPattern("Map").asString()).isEqualTo("Map");
    }
}
