package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExMacroCallTest {
  @Test
  void assertExprLines() {
    assertThat(ExMacroCall.assertExpr(ExOp.op("==", ExVar.var("x"), ExInteger.integer(1))).lines(1))
        .containsExactly("  assert x == 1");
  }
}
