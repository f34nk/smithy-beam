package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlApplyTest {
  @Test
  void applyLines() {
    ErlApply apply =
        ErlApply.apply(
            ErlAtom.atom("apply"), new ErlVar("F"), ErlList.list(new ErlVar("A"), new ErlVar("B")));
    assertThat(apply.lines()).containsExactly("apply(F, [A, B])");
  }

  @Test
  void applyAsString() {
    ErlApply apply =
        ErlApply.apply(
            ErlAtom.atom("apply"), new ErlVar("F"), ErlList.list(new ErlVar("A"), new ErlVar("B")));
    assertThat(apply.asString()).isEqualTo("apply(F, [A, B])");
  }
}
