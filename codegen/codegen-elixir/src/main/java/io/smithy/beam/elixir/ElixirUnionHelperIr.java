package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;

final class ElixirUnionHelperIr {
  private ElixirUnionHelperIr() {}

  static List<ExFunction> unionDecodeEncode(UnionShape shape, SymbolProvider sp) {
    String helperName = helperName(shape);
    return List.of(unionDecode(shape, sp, helperName), unionEncode(shape, sp, helperName));
  }

  private static ExFunction unionDecode(UnionShape shape, SymbolProvider sp, String helperName) {
    List<ExCaseBranch> branches = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      branches.add(
          ExCaseBranch.branch(
              singletonListPattern(
                  ExTuplePattern.tuple(
                      ExStringPattern.string(wireKey), ExVarPattern.var("v"))),
              ExTuple.tuple(ExAtom.atom(tag), ExVar.var("v"))));
    }
    branches.add(
        ExCaseBranch.branch(
            singletonListPattern(
                ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("_v"))),
            ExTuple.tuple(ExAtom.atom("unknown"), ExVar.var("k"))));
    branches.add(ExCaseBranch.branch(ExVarPattern.var("_"), ExAtom.atom("nil")));

    return ExFunction.defpFunction(
        "decode_" + helperName,
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                ExCase.caseExpr(
                    ExCall.call("Map", "to_list", ExVar.var("map")),
                    branches.toArray(ExCaseBranch[]::new))),
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil"))));
  }

  private static ExFunction unionEncode(UnionShape shape, SymbolProvider sp, String helperName) {
    List<ExClause> clauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      clauses.add(
          ExClause.inlineClause(
              List.of(
                  ExTuplePattern.tuple(
                      ExAtomPattern.atom(tag), ExVarPattern.var("v"))),
              ExMap.map(ExMapEntry.entry(ExString.string(wireKey), ExVar.var("v")))));
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(
                ExTuplePattern.tuple(
                    ExAtomPattern.atom("unknown"), ExVarPattern.var("k"))),
            List.of(ExGuard.guard("is_binary", ExVar.var("k"))),
            ExMap.map(ExMapEntry.entry(ExVar.var("k"), ExAtom.atom("nil")))));
    clauses.add(ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")));
    return ExFunction.defpFunction("encode_" + helperName, clauses);
  }

  private static ExConsPattern singletonListPattern(io.smithy.beam.ir.elixir.ExPattern element) {
    return ExConsPattern.consPattern(element, ExNilPattern.nil());
  }

  private static String helperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  static String unionTagForMember(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
  }
}
