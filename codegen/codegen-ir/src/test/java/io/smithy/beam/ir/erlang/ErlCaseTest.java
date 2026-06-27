package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlCaseTest {
    @Test
    void caseExpressionLines() {
        ErlCase caseExpr = ErlCase.caseExpr(
                new ErlVar("X"),
                ErlClause.clause(List.of(new ErlAtomPattern("ok")), new ErlVar("Result")),
                ErlClause.clause(List.of(new ErlAtomPattern("error")), ErlAtom.atom("undefined")));
        assertThat(caseExpr.lines()).containsExactly(
                "case X of",
                "    ok -> Result;",
                "    error -> undefined",
                "end");
    }

    @Test
    void caseExpressionAsString() {
        ErlCase caseExpr = ErlCase.caseExpr(
                new ErlVar("X"),
                ErlClause.clause(List.of(new ErlAtomPattern("ok")), new ErlVar("Result")),
                ErlClause.clause(List.of(new ErlAtomPattern("error")), ErlAtom.atom("undefined")));
        assertThat(caseExpr.asString()).isEqualTo(
                "case X of\n    ok -> Result;\n    error -> undefined\nend");
    }
}
