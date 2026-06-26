package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlListComprehensionTest {
    @Test
    void comprehensionLines() {
        ErlListComprehension lc = ErlListComprehension.comprehension(
                ErlCallLocal.callLocal("decode", new ErlVar("X")),
                new ErlVarPattern("X"),
                new ErlVar("List"));
        assertThat(lc.lines()).containsExactly("[decode(X) || X <- List]");
    }

    @Test
    void comprehensionAsString() {
        ErlListComprehension lc = ErlListComprehension.comprehension(
                ErlCallLocal.callLocal("decode", new ErlVar("X")),
                new ErlVarPattern("X"),
                new ErlVar("List"));
        assertThat(lc.asString()).isEqualTo("[decode(X) || X <- List]");
    }

    @Test
    void comprehensionWithFiltersLines() {
        ErlListComprehension lc = ErlListComprehension.comprehensionWithFilters(
                ErlCallLocal.callLocal("decode", new ErlVar("X")),
                new ErlVarPattern("X"),
                new ErlVar("List"),
                List.of(ErlOp.op("=/=", ErlVar.var("X"), ErlAtom.atom("null"))));
        assertThat(lc.lines()).containsExactly("[decode(X) || X <- List, X =/= null]");
    }

    @Test
    void comprehensionWithFiltersAsString() {
        ErlListComprehension lc = ErlListComprehension.comprehensionWithFilters(
                ErlCallLocal.callLocal("decode", new ErlVar("X")),
                new ErlVarPattern("X"),
                new ErlVar("List"),
                List.of(ErlOp.op("=/=", ErlVar.var("X"), ErlAtom.atom("null"))));
        assertThat(lc.asString()).isEqualTo("[decode(X) || X <- List, X =/= null]");
    }

    @Test
    void comprehensionQualifiersLines() {
        ErlListComprehension lc = ErlListComprehension.comprehensionQualifiers(
                ErlTuple.tuple(
                        ErlVar.var("Fun"),
                        ErlCallLocal.callLocal("make_handler", ErlVar.var("Impl"), ErlVar.var("Fun"))),
                List.of(
                        new ErlComprehensionGenerator(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Fun"),
                                        ErlIntegerPattern.integerPattern(3)),
                                ErlVar.var("Callbacks")),
                        new ErlComprehensionFilter(ErlCall.call(
                                "erlang",
                                "function_exported",
                                ErlVar.var("Impl"),
                                ErlVar.var("Fun"),
                                ErlInteger.integer(3)))));
        assertThat(lc.lines()).containsExactly(
                "[{Fun, make_handler(Impl, Fun)} || {Fun, 3} <- Callbacks,"
                        + " erlang:function_exported(Impl, Fun, 3)]");
    }

    @Test
    void comprehensionQualifiersAsString() {
        ErlListComprehension lc = ErlListComprehension.comprehensionQualifiers(
                ErlTuple.tuple(
                        ErlVar.var("Fun"),
                        ErlCallLocal.callLocal("make_handler", ErlVar.var("Impl"), ErlVar.var("Fun"))),
                List.of(
                        new ErlComprehensionGenerator(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Fun"),
                                        ErlIntegerPattern.integerPattern(3)),
                                ErlVar.var("Callbacks")),
                        new ErlComprehensionFilter(ErlCall.call(
                                "erlang",
                                "function_exported",
                                ErlVar.var("Impl"),
                                ErlVar.var("Fun"),
                                ErlInteger.integer(3)))));
        assertThat(lc.asString()).isEqualTo(
                "[{Fun, make_handler(Impl, Fun)} || {Fun, 3} <- Callbacks,"
                        + " erlang:function_exported(Impl, Fun, 3)]");
    }
}
