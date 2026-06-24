package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlBinPatternTest {
    @Test
    void binPatternLines() {
        assertThat(ErlBinPattern.binPattern("Name/binary, _/binary").lines())
                .containsExactly("<<Name/binary, _/binary>>");
    }

    @Test
    void binPatternAsString() {
        assertThat(ErlBinPattern.binPattern("Name/binary, _/binary").asString())
                .isEqualTo("<<Name/binary, _/binary>>");
    }
}
