package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class ErlangUnionHelperIr {
  private ErlangUnionHelperIr() {}

  static List<ErlFunction> unionDecodeEncode(UnionShape shape, SymbolProvider sp) {
    String helperName = sp.toSymbol(shape).getName().replace("()", "");
    return List.of(unionDecode(shape, sp, helperName), unionEncode(shape, sp, helperName));
  }

  private static ErlFunction unionDecode(UnionShape shape, SymbolProvider sp, String helperName) {
    List<ErlClause> caseClauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      caseClauses.add(
          ErlClause.clause(
              List.of(
                  singletonListPattern(
                      ErlTuplePattern.tuplePattern(
                          ErlBinaryPattern.binaryPattern(wireKey), ErlVarPattern.varPattern("V")))),
              ErlTuple.tuple(ErlAtom.atom(tag), ErlVar.var("V"))));
    }
    caseClauses.add(
        ErlClause.clause(
            List.of(
                singletonListPattern(
                    ErlTuplePattern.tuplePattern(
                        ErlVarPattern.varPattern("K"), ErlVarPattern.varPattern("_V")))),
            ErlTuple.tuple(ErlAtom.atom("unknown"), ErlVar.var("K"))));
    caseClauses.add(
        ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), ErlAtom.atom("undefined")));

    return ErlFunction.function(
        "decode_" + helperName,
        1,
        List.of(
            ErlClause.clause(
                List.of(new ErlRecordPattern("", List.of(), "Map")),
                new ErlCase(ErlCall.call("maps", "to_list", ErlVar.var("Map")), caseClauses)),
            ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
            ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("null")), ErlAtom.atom("undefined"))));
  }

  private static ErlFunction unionEncode(UnionShape shape, SymbolProvider sp, String helperName) {
    List<ErlClause> clauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      clauses.add(
          ErlClause.clause(
              List.of(
                  ErlTuplePattern.tuplePattern(
                      ErlAtomPattern.atomPattern(tag), ErlVarPattern.varPattern("V"))),
              ErlMap.map(ErlMapEntry.entry(ErlBinary.binary(wireKey), ErlVar.var("V")))));
    }
    clauses.add(
        ErlClause.clause(
            List.of(
                ErlTuplePattern.tuplePattern(
                    ErlAtomPattern.atomPattern("unknown"), ErlVarPattern.varPattern("K"))),
            List.of(ErlGuard.guard("is_binary", ErlVar.var("K"))),
            ErlMap.map(ErlMapEntry.entry(ErlVar.var("K"), ErlAtom.atom("null")))));
    clauses.add(
        ErlClause.clause(
            List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")));
    return ErlFunction.function("encode_" + helperName, 1, clauses);
  }

  private static ErlConsPattern singletonListPattern(ErlPattern element) {
    return ErlConsPattern.consPattern(element, ErlNilPattern.nilPattern());
  }

  static String unionTagForMember(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
  }
}
