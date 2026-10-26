package io.smithy.beam.gleam;

import io.beam.lang.gleam.AnonFun;
import io.beam.lang.gleam.CaseClause;
import io.beam.lang.gleam.CaseExpr;
import io.beam.lang.gleam.ConstructorExpr;
import io.beam.lang.gleam.ConstructorPattern;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.LocalCallExpr;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.PatternField;
import io.beam.lang.gleam.PipeExpr;
import io.beam.lang.gleam.PipeStep;
import io.beam.lang.gleam.RemoteCallExpr;
import io.beam.lang.gleam.StringExpr;
import io.beam.lang.gleam.StringPattern;
import io.beam.lang.gleam.Variable;
import io.beam.lang.gleam.VariablePattern;
import io.beam.lang.gleam.IntegerExpr;
import io.beam.lang.gleam.IntegerPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.EnumValueTrait;

final class GleamEnumHelperDsl {

  private GleamEnumHelperDsl() {}

  static List<Function> enumHelpers(EnumShape shape, SymbolProvider sp, String typesModule) {
    List<Function> functions = new ArrayList<>();
    functions.add(toJson(shape, sp, typesModule));
    functions.add(decoder(shape, sp, typesModule));
    functions.add(jsonTo(shape, sp, typesModule));
    return functions;
  }

  static List<Function> intEnumHelpers(IntEnumShape shape, SymbolProvider sp, String typesModule) {
    List<Function> functions = new ArrayList<>();
    functions.add(intToJson(shape, sp, typesModule));
    functions.add(intDecoder(shape, sp, typesModule));
    functions.add(jsonToInt(shape, sp, typesModule));
    return functions;
  }

  private static Function toJson(EnumShape shape, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, shape);
    String unknown =
        GleamCodecNames.qualifiedConstructor(
            typesModule, GleamCodecNames.unknownConstructor(sp, shape));
    List<CaseClause> clauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wire =
          member
              .getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(member.getMemberName());
      String ctor =
          GleamCodecNames.qualifiedConstructor(
              typesModule,
              GleamCodecNames.constructorForMember(sp, shape, member.getMemberName()));
      clauses.add(
          CaseClause.of(
              ConstructorPattern.of(ctor),
              RemoteCallExpr.of("json", "string", List.of(StringExpr.of(wire)))));
    }
    clauses.add(
        CaseClause.of(
            ConstructorPattern.of(
                unknown, List.of(PatternField.positional(VariablePattern.of("value")))),
            RemoteCallExpr.of("json", "string", List.of(Variable.of("value")))));
    return Function.pub(
        GleamCodecNames.toJsonName(shape),
        List.of(Param.of("value", type)),
        "json.Json",
        CaseExpr.of(Variable.of("value"), clauses));
  }

  private static Function decoder(EnumShape shape, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, shape);
    String unknown =
        GleamCodecNames.qualifiedConstructor(
            typesModule, GleamCodecNames.unknownConstructor(sp, shape));
    List<CaseClause> clauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String wire =
          member
              .getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(member.getMemberName());
      String ctor =
          GleamCodecNames.qualifiedConstructor(
              typesModule,
              GleamCodecNames.constructorForMember(sp, shape, member.getMemberName()));
      clauses.add(
          CaseClause.of(StringPattern.of(wire), ConstructorExpr.of(ctor)));
    }
    clauses.add(
        CaseClause.of(
            VariablePattern.of("other"),
            ConstructorExpr.positional(unknown, List.of(Variable.of("other")))));
    return Function.pub(
        GleamCodecNames.decoderName(shape),
        List.of(),
        "decode.Decoder(" + type + ")",
        PipeExpr.of(
            Variable.of("decode.string"),
            List.of(
                PipeStep.of(
                    RemoteCallExpr.of(
                        "decode",
                        "map",
                        List.of(
                            AnonFun.of(
                                "wire",
                                CaseExpr.of(Variable.of("wire"), clauses))))))));
  }

  private static Function jsonTo(EnumShape shape, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, shape);
    return Function.pub(
        GleamCodecNames.jsonToName(shape),
        List.of(Param.of("data", "dynamic.Dynamic")),
        "Result(" + type + ", List(decode.DecodeError))",
        RemoteCallExpr.of(
            "decode",
            "run",
            List.of(
                Variable.of("data"),
                LocalCallExpr.of(GleamCodecNames.decoderName(shape), List.of()))));
  }

  private static Function intToJson(IntEnumShape shape, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, shape);
    String unknown =
        GleamCodecNames.qualifiedConstructor(
            typesModule, GleamCodecNames.unknownConstructor(sp, shape));
    List<CaseClause> clauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      int wire = member.expectTrait(EnumValueTrait.class).expectIntValue();
      String ctor =
          GleamCodecNames.qualifiedConstructor(
              typesModule,
              GleamCodecNames.constructorForMember(sp, shape, member.getMemberName()));
      clauses.add(
          CaseClause.of(
              ConstructorPattern.of(ctor),
              RemoteCallExpr.of("json", "int", List.of(IntegerExpr.of(wire)))));
    }
    clauses.add(
        CaseClause.of(
            ConstructorPattern.of(
                unknown, List.of(PatternField.positional(VariablePattern.of("value")))),
            RemoteCallExpr.of("json", "int", List.of(Variable.of("value")))));
    return Function.pub(
        GleamCodecNames.toJsonName(shape),
        List.of(Param.of("value", type)),
        "json.Json",
        CaseExpr.of(Variable.of("value"), clauses));
  }

  private static Function intDecoder(IntEnumShape shape, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, shape);
    String unknown =
        GleamCodecNames.qualifiedConstructor(
            typesModule, GleamCodecNames.unknownConstructor(sp, shape));
    List<CaseClause> clauses = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      int wire = member.expectTrait(EnumValueTrait.class).expectIntValue();
      String ctor =
          GleamCodecNames.qualifiedConstructor(
              typesModule,
              GleamCodecNames.constructorForMember(sp, shape, member.getMemberName()));
      clauses.add(CaseClause.of(IntegerPattern.of(wire), ConstructorExpr.of(ctor)));
    }
    clauses.add(
        CaseClause.of(
            VariablePattern.of("other"),
            ConstructorExpr.positional(unknown, List.of(Variable.of("other")))));
    return Function.pub(
        GleamCodecNames.decoderName(shape),
        List.of(),
        "decode.Decoder(" + type + ")",
        PipeExpr.of(
            Variable.of("decode.int"),
            List.of(
                PipeStep.of(
                    RemoteCallExpr.of(
                        "decode",
                        "map",
                        List.of(
                            AnonFun.of(
                                "wire",
                                CaseExpr.of(Variable.of("wire"), clauses))))))));
  }

  private static Function jsonToInt(IntEnumShape shape, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, shape);
    return Function.pub(
        GleamCodecNames.jsonToName(shape),
        List.of(Param.of("data", "dynamic.Dynamic")),
        "Result(" + type + ", List(decode.DecodeError))",
        RemoteCallExpr.of(
            "decode",
            "run",
            List.of(
                Variable.of("data"),
                LocalCallExpr.of(GleamCodecNames.decoderName(shape), List.of()))));
  }
}
