package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;

import java.util.List;

final class ErlangXmlCodecIr {
    private ErlangXmlCodecIr() {}

    public static ErlFunction decodeSparseMap() {
        ErlFun sparseMapFun = ErlFun.fun(
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_K"), ErlAtomPattern.atomPattern("null")),
                        ErlAtom.atom("undefined")),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_K"), ErlVarPattern.varPattern("V")),
                        ErlVar.var("V")));

        return ErlFunction.function(
                "decode_sparse_map",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Map")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                                ErlCall.call("maps", "map", sparseMapFun, ErlVar.var("Map")))));
    }
}
