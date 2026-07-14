package io.smithy.beam.elixir;

import io.beam.ir.elixir.AnonFun;
import io.beam.ir.elixir.AnonFunClause;
import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.AtomPattern;
import io.beam.ir.elixir.BlockExpr;
import io.beam.ir.elixir.CaseExpr;
import io.beam.ir.elixir.Clause;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.Guard;
import io.beam.ir.elixir.IfExpr;
import io.beam.ir.elixir.InfixExpr;
import io.beam.ir.elixir.IntegerExpr;
import io.beam.ir.elixir.IntegerPattern;
import io.beam.ir.elixir.IsTypeGuard;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.MatchExpr;
import io.beam.ir.elixir.NilExpr;
import io.beam.ir.elixir.NilPattern;
import io.beam.ir.elixir.Pattern;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.StringExpr;
import io.beam.ir.elixir.StringPattern;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.beam.ir.elixir.WildcardPattern;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.EnumValueTrait;

final class ElixirEnumHelperIr {
  private ElixirEnumHelperIr() {}

  static List<Function> enumDecodeEncode(EnumShape shape, SymbolProvider sp) {
    String helperName = helperName(shape);
    List<Function> functions = new ArrayList<>();
    functions.addAll(decodeEnum(shape, sp, helperName));
    functions.addAll(encodeEnum(shape, sp, helperName));
    functions.addAll(enumListDecode(helperName));
    functions.addAll(enumListEncode(helperName));
    return functions;
  }

  static List<Function> intEnumDecodeEncode(IntEnumShape shape, SymbolProvider sp) {
    String helperName = helperName(shape);
    List<Function> functions = new ArrayList<>();
    functions.addAll(decodeIntEnum(shape, sp, helperName));
    functions.addAll(encodeIntEnum(shape, sp, helperName));
    functions.addAll(enumListDecode(helperName));
    functions.addAll(enumListEncode(helperName));
    return functions;
  }

  static BlockExpr enumStringDecodeFallbackBody(String decodeFunctionName) {
    return new BlockExpr(
        List.of(
            MatchExpr.bind(
                "normalized",
                RemoteCallExpr.of(
                    "String",
                    "replace",
                    List.of(Variable.of("v"), StringExpr.of("_"), StringExpr.of(".")))),
            new IfExpr(
                new InfixExpr(Variable.of("normalized"), "==", Variable.of("v")),
                TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v"))),
                new CaseExpr(
                    LocalCallExpr.of(decodeFunctionName, List.of(Variable.of("normalized"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("unknown"), WildcardPattern.of())),
                            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v")))),
                        Clause.of(VariablePattern.of("result"), Variable.of("result")))),
                false)));
  }

  private static List<Function> decodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
    List<Function> functions = new ArrayList<>();
    String name = "decode_" + helperName;
    for (MemberShape member : shape.members()) {
      String wireValue =
          member
              .getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(member.getMemberName());
      functions.add(
          defp(
              name,
              List.of(StringPattern.of(wireValue)),
              AtomExpr.of(enumAtomForMember(sp, shape, member.getMemberName())),
              true));
    }
    functions.add(
        defp(
            name,
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_binary", "v"),
            enumStringDecodeFallbackBody(name),
            false));
    functions.add(defp(name, List.of(NilPattern.of()), NilExpr.of(), true));
    return functions;
  }

  private static List<Function> encodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
    List<Function> functions = new ArrayList<>();
    String name = "encode_" + helperName;
    for (MemberShape member : shape.members()) {
      String wireValue =
          member
              .getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(member.getMemberName());
      functions.add(
          defp(
              name,
              List.of(AtomPattern.of(enumAtomForMember(sp, shape, member.getMemberName()))),
              StringExpr.of(wireValue),
              true));
    }
    functions.add(
        defp(
            name,
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("v")))),
            IsTypeGuard.of("is_binary", "v"),
            Variable.of("v"),
            true));
    functions.add(defp(name, List.of(NilPattern.of()), NilExpr.of(), true));
    return functions;
  }

  private static List<Function> decodeIntEnum(
      IntEnumShape shape, SymbolProvider sp, String helperName) {
    List<Function> functions = new ArrayList<>();
    String name = "decode_" + helperName;
    for (MemberShape member : shape.members()) {
      int wireValue = member.expectTrait(EnumValueTrait.class).expectIntValue();
      functions.add(
          defp(
              name,
              List.of(IntegerPattern.of(wireValue)),
              AtomExpr.of(enumAtomForMember(sp, shape, member.getMemberName())),
              true));
    }
    functions.add(
        defp(
            name,
            List.of(VariablePattern.of("v")),
            IsTypeGuard.of("is_integer", "v"),
            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v"))),
            true));
    functions.add(defp(name, List.of(NilPattern.of()), NilExpr.of(), true));
    return functions;
  }

  private static List<Function> encodeIntEnum(
      IntEnumShape shape, SymbolProvider sp, String helperName) {
    List<Function> functions = new ArrayList<>();
    String name = "encode_" + helperName;
    for (MemberShape member : shape.members()) {
      int wireValue = member.expectTrait(EnumValueTrait.class).expectIntValue();
      functions.add(
          defp(
              name,
              List.of(AtomPattern.of(enumAtomForMember(sp, shape, member.getMemberName()))),
              IntegerExpr.of(wireValue),
              true));
    }
    functions.add(
        defp(
            name,
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("v")))),
            IsTypeGuard.of("is_integer", "v"),
            Variable.of("v"),
            true));
    functions.add(defp(name, List.of(NilPattern.of()), NilExpr.of(), true));
    return functions;
  }

  private static List<Function> enumListDecode(String helperName) {
    String name = "decode_" + helperName + "_list";
    return List.of(
        defp(name, List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            name,
            List.of(VariablePattern.of("list")),
            IsTypeGuard.of("is_list", "list"),
            RemoteCallExpr.of(
                "Enum",
                "map",
                List.of(
                    Variable.of("list"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(
                                List.of(VariablePattern.of("v")),
                                LocalCallExpr.of(
                                    "decode_" + helperName, List.of(Variable.of("v")))))))),
            false));
  }

  private static List<Function> enumListEncode(String helperName) {
    String name = "encode_" + helperName + "_list";
    return List.of(
        defp(name, List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            name,
            List.of(VariablePattern.of("list")),
            IsTypeGuard.of("is_list", "list"),
            RemoteCallExpr.of(
                "Enum",
                "map",
                List.of(
                    Variable.of("list"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(
                                List.of(VariablePattern.of("v")),
                                LocalCallExpr.of(
                                    "encode_" + helperName, List.of(Variable.of("v")))))))),
            false));
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, Guard guard, Expression body, boolean oneLiner) {
    return new Function(
        name, true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
  }

  private static String helperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  private static String enumAtomForMember(SymbolProvider sp, Shape enumShape, String memberName) {
    @SuppressWarnings("unchecked")
    Map<String, String> byMember =
        sp.toSymbol(enumShape).getProperty("enumAtomByMember", Map.class).orElseThrow();
    return byMember.get(memberName);
  }
}
