package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlFunTest {
  @Test
  void funExpressionLines() {
    ErlFun fun = ErlFun.fun(ErlClause.clause(List.of(new ErlVarPattern("V")), new ErlVar("V")));
    assertThat(fun.lines()).containsExactly("fun(V) ->", "    V", "end");
  }

  @Test
  void funExpressionAsString() {
    ErlFun fun = ErlFun.fun(ErlClause.clause(List.of(new ErlVarPattern("V")), new ErlVar("V")));
    assertThat(fun.asString()).isEqualTo("fun(V) ->\n    V\nend");
  }
}
