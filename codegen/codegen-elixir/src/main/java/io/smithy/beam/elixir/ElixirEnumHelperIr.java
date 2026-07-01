package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
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

  static List<ExFunction> enumDecodeEncode(EnumShape shape, SymbolProvider sp) {
    String helperName = helperName(shape);
    return List.of(decodeEnum(shape, sp, helperName), encodeEnum(shape, sp, helperName));
  }

  static List<ExFunction> intEnumDecodeEncode(IntEnumShape shape, SymbolProvider sp) {
    String helperName = helperName(shape);
    return List.of(decodeIntEnum(shape, sp, helperName), encodeIntEnum(shape, sp, helperName));
  }

  private static ExFunction decodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
    List<ExClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      String wireValue =
          m.getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(m.getMemberName());
      clauses.add(
          ExClause.inlineClause(
              List.of(ExStringPattern.string(wireValue)),
              ExAtom.atom(enumAtomForMember(sp, shape, m.getMemberName()))));
    }
    clauses.add(
        ExClause.blockClause(
            List.of(ExVarPattern.var("v")),
            List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
            enumStringDecodeFallbackBody("decode_" + helperName)));
    clauses.add(ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")));
    return ExFunction.defpFunction("decode_" + helperName, clauses);
  }

  static ExExpr[] enumStringDecodeFallbackBody(String decodeFunctionName) {
    return new ExExpr[] {
      ExMatch.match(
          ExVarPattern.var("normalized"),
          ExCall.call(
              "String", "replace", ExVar.var("v"), ExString.string("_"), ExString.string("."))),
      ExIf.ifBlock(
          ExOp.op("==", ExVar.var("normalized"), ExVar.var("v")),
          ExTuple.tuple(ExAtom.atom("unknown"), ExVar.var("v")),
          ExCallLocal.callLocal(decodeFunctionName, ExVar.var("normalized")))
    };
  }

  private static ExFunction encodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
    List<ExClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      String wireValue =
          m.getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(m.getMemberName());
      clauses.add(
          ExClause.inlineClause(
              List.of(ExAtomPattern.atom(enumAtomForMember(sp, shape, m.getMemberName()))),
              ExString.string(wireValue)));
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(ExAtomPattern.atom("unknown"), ExVarPattern.var("v"))),
            List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
            ExVar.var("v")));
    clauses.add(ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")));
    return ExFunction.defpFunction("encode_" + helperName, clauses);
  }

  private static ExFunction decodeIntEnum(
      IntEnumShape shape, SymbolProvider sp, String helperName) {
    List<ExClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
      clauses.add(
          ExClause.inlineClause(
              List.of(ExIntegerPattern.integer(wireValue)),
              ExAtom.atom(enumAtomForMember(sp, shape, m.getMemberName()))));
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("v")),
            List.of(ExGuard.guard("is_integer", ExVar.var("v"))),
            ExTuple.tuple(ExAtom.atom("unknown"), ExVar.var("v"))));
    clauses.add(ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")));
    return ExFunction.defpFunction("decode_" + helperName, clauses);
  }

  private static ExFunction encodeIntEnum(
      IntEnumShape shape, SymbolProvider sp, String helperName) {
    List<ExClause> clauses = new ArrayList<>();
    for (MemberShape m : shape.members()) {
      int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
      clauses.add(
          ExClause.inlineClause(
              List.of(ExAtomPattern.atom(enumAtomForMember(sp, shape, m.getMemberName()))),
              ExInteger.integer(wireValue)));
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(ExAtomPattern.atom("unknown"), ExVarPattern.var("v"))),
            List.of(ExGuard.guard("is_integer", ExVar.var("v"))),
            ExVar.var("v")));
    clauses.add(ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")));
    return ExFunction.defpFunction("encode_" + helperName, clauses);
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
