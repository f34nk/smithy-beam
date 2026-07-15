package io.smithy.beam.elixir;

import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.AtomPattern;
import io.beam.ir.elixir.CaseExpr;
import io.beam.ir.elixir.Clause;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.Guard;
import io.beam.ir.elixir.IsTypeGuard;
import io.beam.ir.elixir.ListPattern;
import io.beam.ir.elixir.MapEntry;
import io.beam.ir.elixir.MapExpr;
import io.beam.ir.elixir.NilExpr;
import io.beam.ir.elixir.NilPattern;
import io.beam.ir.elixir.Pattern;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.StringPattern;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.beam.ir.elixir.WildcardPattern;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;

final class ElixirUnionHelperIr {
  private ElixirUnionHelperIr() {}

  static List<Function> unionDecodeEncode(UnionShape shape, SymbolProvider sp) {
    String helperName = helperName(shape);
    List<Function> functions = new ArrayList<>();
    functions.addAll(unionDecode(shape, sp, helperName));
    functions.addAll(unionEncode(shape, sp, helperName));
    return functions;
  }

  private static List<Function> unionDecode(
      UnionShape shape, SymbolProvider sp, String helperName) {
    List<Clause> branches = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      branches.add(
          Clause.of(
              singletonListPattern(
                  TuplePattern.of(List.of(StringPattern.of(wireKey), VariablePattern.of("v")))),
              TupleExpr.of(List.of(AtomExpr.of(tag), Variable.of("v")))));
    }
    branches.add(
        Clause.of(
            singletonListPattern(
                TuplePattern.of(List.of(VariablePattern.of("k"), VariablePattern.of("_v")))),
            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("k")))));
    branches.add(Clause.of(WildcardPattern.of(), NilExpr.of()));

    return List.of(
        new Function(
            "decode_" + helperName,
            true,
            List.of(
                FunctionHead.of(
                    List.of(VariablePattern.of("map")), IsTypeGuard.of("is_map", "map"))),
            new CaseExpr(
                RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("map"))), branches),
            null,
            null,
            false),
        new Function(
            "decode_" + helperName,
            true,
            List.of(FunctionHead.of(List.of(NilPattern.of()))),
            NilExpr.of(),
            null,
            null,
            true));
  }

  private static List<Function> unionEncode(
      UnionShape shape, SymbolProvider sp, String helperName) {
    List<Function> functions = new ArrayList<>();
    String name = "encode_" + helperName;
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      functions.add(
          defp(
              name,
              List.of(TuplePattern.of(List.of(AtomPattern.of(tag), VariablePattern.of("v")))),
              MapExpr.of(List.of(MapEntry.stringKey(wireKey, Variable.of("v")))),
              true));
    }
    functions.add(
        defp(
            name,
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("k")))),
            IsTypeGuard.of("is_binary", "k"),
            MapExpr.of(List.of(MapEntry.pair(Variable.of("k"), NilExpr.of()))),
            false));
    functions.add(defp(name, List.of(NilPattern.of()), NilExpr.of(), true));
    return functions;
  }

  private static ListPattern singletonListPattern(Pattern element) {
    return ListPattern.of(List.of(element));
  }

  private static Function defp(
      String name,
      List<Pattern> params,
      Guard guard,
      io.beam.ir.elixir.Expression body,
      boolean oneLiner) {
    return new Function(
        name, true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, io.beam.ir.elixir.Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static String helperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  static String unionTagForMember(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
  }
}
