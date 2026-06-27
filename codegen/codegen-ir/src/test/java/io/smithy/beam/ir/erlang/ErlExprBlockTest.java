package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlExprBlockTest {
  private static ErlExprBlock matchInBlock() {
    return ErlExprBlock.block(
        ErlMatch.match(new ErlVarPattern("X"), ErlCallLocal.callLocal("fetch")), new ErlVar("X"));
  }

  @Test
  void matchInBlockLines() {
    assertThat(matchInBlock().lines(1)).containsExactly("    X = fetch(),", "    X");
  }

  @Test
  void matchInBlockAsString() {
    assertThat(matchInBlock().asString(1)).isEqualTo("    X = fetch(),\n    X");
  }
}
