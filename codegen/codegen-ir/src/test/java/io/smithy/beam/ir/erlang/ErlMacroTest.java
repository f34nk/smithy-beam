package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlMacroTest {
  @Test
  void macroAsString() {
    assertThat(ErlMacro.macro("HANDLERS_KEY").asString()).isEqualTo("?HANDLERS_KEY");
  }
}
