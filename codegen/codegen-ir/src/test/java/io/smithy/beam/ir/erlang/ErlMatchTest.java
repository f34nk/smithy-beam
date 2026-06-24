package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlMatchTest {
    @Test
    void matchLines() {
        ErlMatch match = ErlMatch.match(
                new ErlVarPattern("Value"),
                ErlCallLocal.callLocal("fetch"));
        assertThat(match.lines()).containsExactly("Value = fetch()");
    }

    @Test
    void matchAsString() {
        ErlMatch match = ErlMatch.match(
                new ErlVarPattern("Value"),
                ErlCallLocal.callLocal("fetch"));
        assertThat(match.asString()).isEqualTo("Value = fetch()");
    }
}
