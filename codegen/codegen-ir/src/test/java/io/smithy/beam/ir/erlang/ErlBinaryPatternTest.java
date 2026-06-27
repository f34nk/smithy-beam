package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlBinaryPatternTest {
    @Test
    void binaryPatternLines() {
        assertThat(ErlBinaryPattern.binaryPattern("payload").lines()).containsExactly("<<\"payload\">>");
    }

    @Test
    void binaryPatternAsString() {
        assertThat(ErlBinaryPattern.binaryPattern("payload").asString()).isEqualTo("<<\"payload\">>");
    }
}
