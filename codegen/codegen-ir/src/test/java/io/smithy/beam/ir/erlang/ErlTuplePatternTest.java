package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlTuplePatternTest {
    @Test
    void tuplePatternLines() {
        ErlTuplePattern pattern = ErlTuplePattern.tuplePattern(
                new ErlVarPattern("A"), new ErlVarPattern("B"));
        assertThat(pattern.lines()).containsExactly("{A, B}");
    }

    @Test
    void tuplePatternAsString() {
        ErlTuplePattern pattern = ErlTuplePattern.tuplePattern(
                new ErlVarPattern("A"), new ErlVarPattern("B"));
        assertThat(pattern.asString()).isEqualTo("{A, B}");
    }
}
