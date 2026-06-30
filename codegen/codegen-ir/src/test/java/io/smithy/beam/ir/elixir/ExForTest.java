package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExForTest {
  @Test
  void forIntoExprRendersIntoClause() {
    ExFor comprehension =
        ExFor.forIntoExpr(
            ExTuple.tuple(ExVar.var("fun"), ExVar.var("handler")),
            ExTuplePattern.tuple(ExVarPattern.var("fun"), ExIntegerPattern.integer(3)),
            ExCall.call("BasicServiceBehaviour", "callbacks"),
            ExMap.map(),
            ExForFilter.filter(
                ExCallLocal.callLocal(
                    "function_exported?",
                    ExVar.var("impl"),
                    ExVar.var("fun"),
                    ExInteger.integer(3))));
    assertThat(comprehension.lines(1))
        .containsExactly(
            "  for {fun, 3} <-",
            "    BasicServiceBehaviour.callbacks(),",
            "    function_exported?(impl, fun, 3),",
            "    into: %{} do",
            "    {fun, handler}",
            "  end");
  }
}
