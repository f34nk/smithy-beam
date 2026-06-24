package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlTryTest {
    @Test
    void tryExpressionLines() {
        ErlTry tryExpr = ErlTry.tryExpr(
                List.of(ErlCallLocal.callLocal("fetch")),
                List.of(ErlCatchClause.catchClause(
                        new ErlVarPattern("Class"),
                        new ErlVarPattern("Reason"),
                        ErlAtom.atom("error"))));
        assertThat(tryExpr.lines()).containsExactly(
                "try",
                "    fetch()",
                "catch",
                "    Class:Reason -> error",
                "end");
    }

    @Test
    void tryExpressionAsString() {
        ErlTry tryExpr = ErlTry.tryExpr(
                List.of(ErlCallLocal.callLocal("fetch")),
                List.of(ErlCatchClause.catchClause(
                        new ErlVarPattern("Class"),
                        new ErlVarPattern("Reason"),
                        ErlAtom.atom("error"))));
        assertThat(tryExpr.asString()).isEqualTo(
                "try\n    fetch()\ncatch\n    Class:Reason -> error\nend");
    }
}
