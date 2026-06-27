package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExCaseTest {
  @Test
  void caseExpressionAsString() {
    ExCase caseExpr =
        ExCase.caseExpr(
            ExVar.var("value"),
            ExCaseBranch.branch(ExAtomPattern.atom("ok"), ExVar.var("result")),
            ExCaseBranch.branch(ExAtomPattern.atom("error"), ExAtom.atom("nil")));
    assertThat(caseExpr.asString())
        .isEqualTo("case value do\n  :ok -> result\n  :error -> :nil\nend");
  }
}
