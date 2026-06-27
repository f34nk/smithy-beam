package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlIntegerPatternTest {
    @Test
    void lines() {
        assertThat(new ErlIntegerPattern(0).lines()).containsExactly("0");
    }

    @Test
    void asString() {
        assertThat(new ErlIntegerPattern(0).asString()).isEqualTo("0");
    }
}
