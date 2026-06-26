package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlMacroTest {
    @Test
    void macroAsString() {
        assertThat(ErlMacro.macro("HANDLERS_KEY").asString()).isEqualTo("?HANDLERS_KEY");
    }
}
