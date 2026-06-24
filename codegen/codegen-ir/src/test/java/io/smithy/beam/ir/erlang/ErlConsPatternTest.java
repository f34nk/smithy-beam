package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlConsPatternTest {
    @Test
    void consPatternLines() {
        ErlConsPattern pattern = ErlConsPattern.consPattern(
                new ErlVarPattern("H"), new ErlVarPattern("T"));
        assertThat(pattern.lines()).containsExactly("[H | T]");
    }

    @Test
    void consPatternAsString() {
        ErlConsPattern pattern = ErlConsPattern.consPattern(
                new ErlVarPattern("H"), new ErlVarPattern("T"));
        assertThat(pattern.asString()).isEqualTo("[H | T]");
    }
}
