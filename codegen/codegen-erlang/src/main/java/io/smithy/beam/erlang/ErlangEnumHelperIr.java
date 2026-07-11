package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.IntegerExpr;
import io.beam.ir.erlang.IntegerPattern;
import io.beam.ir.erlang.IsTypeGuard;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.EnumValueTrait;

final class ErlangEnumHelperIr {
  private ErlangEnumHelperIr() {}

  static List<Function> enumDecodeEncode(EnumShape shape, SymbolProvider sp) {
    String helperName = helperName(sp, shape);
    return List.of(
        decodeEnum(shape, sp, helperName),
        encodeEnum(shape, sp, helperName),
        enumListDecode(helperName),
        enumListEncode(helperName));
  }

  static List<Function> intEnumDecodeEncode(IntEnumShape shape, SymbolProvider sp) {
    String helperName = helperName(sp, shape);
    return List.of(
        decodeIntEnum(shape, sp, helperName),
        encodeIntEnum(shape, sp, helperName),
        enumListDecode(helperName),
        enumListEncode(helperName));
  }

  private static Function decodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      String wireValue =
          m.getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(m.getMemberName());
      clauses.add(
          FunctionClause.of(
              List.of(BinaryPattern.of(wireValue)),
              AtomExpr.of(enumAtomForMember(sp, shape, m.getMemberName()))));
    }
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("V")),
            IsTypeGuard.of("binary", Variable.of("V")),
            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("V")))));
    clauses.add(FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")));
    clauses.add(FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")));
    return Function.of("decode_" + helperName, clauses);
  }

  private static Function encodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      String wireValue =
          m.getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(m.getMemberName());
      clauses.add(
          FunctionClause.of(
              List.of(AtomPattern.of(enumAtomForMember(sp, shape, m.getMemberName()))),
              BinaryExpr.of(wireValue)));
    }
    clauses.add(
        FunctionClause.of(
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("V")))),
            IsTypeGuard.of("binary", Variable.of("V")),
            Variable.of("V")));
    clauses.add(FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")));
    return Function.of("encode_" + helperName, clauses);
  }

  private static Function decodeIntEnum(IntEnumShape shape, SymbolProvider sp, String helperName) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
      clauses.add(
          FunctionClause.of(
              List.of(IntegerPattern.of(wireValue)),
              AtomExpr.of(enumAtomForMember(sp, shape, m.getMemberName()))));
    }
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("V")),
            IsTypeGuard.of("integer", Variable.of("V")),
            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("V")))));
    clauses.add(FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")));
    clauses.add(FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")));
    return Function.of("decode_" + helperName, clauses);
  }

  private static Function encodeIntEnum(IntEnumShape shape, SymbolProvider sp, String helperName) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
      clauses.add(
          FunctionClause.of(
              List.of(AtomPattern.of(enumAtomForMember(sp, shape, m.getMemberName()))),
              IntegerExpr.of(wireValue)));
    }
    clauses.add(
        FunctionClause.of(
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("V")))),
            IsTypeGuard.of("integer", Variable.of("V")),
            Variable.of("V")));
    clauses.add(FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")));
    return Function.of("encode_" + helperName, clauses);
  }

  private static Function enumListDecode(String helperName) {
    return Function.of(
        "decode_" + helperName + "_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("decode_" + helperName, List.of(Variable.of("V"))),
                    VariablePattern.of("V"),
                    Variable.of("List"),
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("null"))))));
  }

  private static Function enumListEncode(String helperName) {
    return Function.of(
        "encode_" + helperName + "_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("encode_" + helperName, List.of(Variable.of("V"))),
                    VariablePattern.of("V"),
                    Variable.of("List"),
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined"))))));
  }

  private static String helperName(SymbolProvider sp, Shape shape) {
    return sp.toSymbol(shape).getName().replace("()", "");
  }

  private static String enumAtomForMember(SymbolProvider sp, Shape enumShape, String memberName) {
    @SuppressWarnings("unchecked")
    Map<String, String> byMember =
        sp.toSymbol(enumShape).getProperty("enumAtomByMember", Map.class).orElseThrow();
    return byMember.get(memberName);
  }
}
