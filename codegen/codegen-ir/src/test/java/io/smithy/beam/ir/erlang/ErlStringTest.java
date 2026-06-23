package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlStringTest {
    @Test
    void lines() {
        assertThat(new ErlString("hello").lines()).containsExactly("\"hello\"");
    }

    @Test
    void asString() {
        assertThat(new ErlString("hello").asString()).isEqualTo("\"hello\"");
    }
}
