package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlListComprehensionTest {
    @Test
    void comprehensionLines() {
        ErlListComprehension lc = ErlListComprehension.comprehension(
                ErlCallLocal.callLocal("decode", new ErlVar("X")),
                new ErlVarPattern("X"),
                new ErlVar("List"));
        assertThat(lc.lines()).containsExactly("[decode(X) || X <- List]");
    }

    @Test
    void comprehensionAsString() {
        ErlListComprehension lc = ErlListComprehension.comprehension(
                ErlCallLocal.callLocal("decode", new ErlVar("X")),
                new ErlVarPattern("X"),
                new ErlVar("List"));
        assertThat(lc.asString()).isEqualTo("[decode(X) || X <- List]");
    }
}
