package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExBehaviourAttrTest {
  @Test
  void behaviourAttrLines() {
    assertThat(ExBehaviourAttr.behaviour("BasicServiceBehaviour").lines(1))
        .containsExactly("  @behaviour BasicServiceBehaviour");
  }

  @Test
  void moduleAssignAttrLines() {
    assertThat(ExModuleAssignAttr.assign("default_impl", ExVar.var("BasicServiceImpl")).lines(1))
        .containsExactly("  @default_impl BasicServiceImpl");
    assertThat(
            ExModuleAssignAttr.assign(
                    "handlers_key",
                    ExTuple.tuple(ExVar.var("BasicServiceServer"), ExAtom.atom("handlers")))
                .lines(1))
        .containsExactly("  @handlers_key {BasicServiceServer, :handlers}");
  }
}
