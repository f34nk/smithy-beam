package io.smithy.beam.erlang;

import io.beam.lang.erlang.AtomExpr;
import io.beam.lang.erlang.AtomPattern;
import io.beam.lang.erlang.BinaryExpr;
import io.beam.lang.erlang.BinaryPattern;
import io.beam.lang.erlang.CaseExpr;
import io.beam.lang.erlang.Clause;
import io.beam.lang.erlang.Function;
import io.beam.lang.erlang.FunctionClause;
import io.beam.lang.erlang.IsTypeGuard;
import io.beam.lang.erlang.ListPattern;
import io.beam.lang.erlang.MapEntry;
import io.beam.lang.erlang.MapExpr;
import io.beam.lang.erlang.MapPattern;
import io.beam.lang.erlang.Pattern;
import io.beam.lang.erlang.RemoteCallExpr;
import io.beam.lang.erlang.TupleExpr;
import io.beam.lang.erlang.TuplePattern;
import io.beam.lang.erlang.Variable;
import io.beam.lang.erlang.VariablePattern;
import io.beam.lang.erlang.WildcardPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class ErlangUnionHelperDsl {
  private ErlangUnionHelperDsl() {}

  static List<Function> unionDecodeEncode(UnionShape shape, SymbolProvider sp) {
    String helperName = sp.toSymbol(shape).getName().replace("()", "");
    return List.of(unionDecode(shape, sp, helperName), unionEncode(shape, sp, helperName));
  }

  private static Function unionDecode(UnionShape shape, SymbolProvider sp, String helperName) {
    List<Clause> caseClauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      caseClauses.add(
          Clause.of(
              singletonListPattern(
                  TuplePattern.of(List.of(BinaryPattern.of(wireKey), VariablePattern.of("V")))),
              TupleExpr.of(List.of(AtomExpr.of(tag), Variable.of("V")))));
    }
    caseClauses.add(
        Clause.of(
            singletonListPattern(
                TuplePattern.of(List.of(VariablePattern.of("K"), WildcardPattern.of()))),
            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("K")))));
    caseClauses.add(Clause.of(WildcardPattern.of(), AtomExpr.of("undefined")));

    return Function.of(
        "decode_" + helperName,
        List.of(
            FunctionClause.of(
                List.of(MapPattern.bind("Map")),
                CaseExpr.of(
                    RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Map"))),
                    caseClauses)),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined"))));
  }

  private static Function unionEncode(UnionShape shape, SymbolProvider sp, String helperName) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wireKey = member.getMemberName();
      String tag = unionTagForMember(sp, member);
      clauses.add(
          FunctionClause.of(
              List.of(TuplePattern.of(List.of(AtomPattern.of(tag), VariablePattern.of("V")))),
              MapExpr.of(List.of(MapEntry.of(BinaryExpr.of(wireKey), Variable.of("V"))))));
    }
    clauses.add(
        FunctionClause.of(
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("K")))),
            IsTypeGuard.of("binary", Variable.of("K")),
            MapExpr.of(List.of(MapEntry.of(Variable.of("K"), AtomExpr.of("null"))))));
    clauses.add(FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")));
    return Function.of("encode_" + helperName, clauses);
  }

  private static ListPattern singletonListPattern(Pattern element) {
    return ListPattern.of(List.of(element));
  }

  static String unionTagForMember(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
  }
}
