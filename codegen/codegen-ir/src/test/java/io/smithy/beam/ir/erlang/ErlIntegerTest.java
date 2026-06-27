package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlIntegerTest {
    @Test
    void lines() {
        assertThat(new ErlInteger(42).lines()).containsExactly("42");
    }

    @Test
    void asString() {
        assertThat(new ErlInteger(42).asString()).isEqualTo("42");
    }
}
