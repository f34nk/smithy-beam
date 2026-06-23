package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlBinaryTest {
    @Test
    void lines() {
        assertThat(new ErlBinary("name").lines()).containsExactly("<<\"name\">>");
    }

    @Test
    void asString() {
        assertThat(new ErlBinary("name").asString()).isEqualTo("<<\"name\">>");
    }
}
