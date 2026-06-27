package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlFunTest {
    @Test
    void funExpressionLines() {
        ErlFun fun = ErlFun.fun(
                ErlClause.clause(List.of(new ErlVarPattern("V")), new ErlVar("V")));
        assertThat(fun.lines()).containsExactly(
                "fun (V) ->",
                "    V",
                "end");
    }

    @Test
    void funExpressionAsString() {
        ErlFun fun = ErlFun.fun(
                ErlClause.clause(List.of(new ErlVarPattern("V")), new ErlVar("V")));
        assertThat(fun.asString()).isEqualTo("fun (V) ->\n    V\nend");
    }
}
