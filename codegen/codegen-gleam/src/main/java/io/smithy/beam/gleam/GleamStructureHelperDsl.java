package io.smithy.beam.gleam;

import io.beam.lang.gleam.BlockExpr;
import io.beam.lang.gleam.ConstructorExpr;
import io.beam.lang.gleam.ConstructorPattern;
import io.beam.lang.gleam.Expression;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.LabelledArg;
import io.beam.lang.gleam.LetExpr;
import io.beam.lang.gleam.ListExpr;
import io.beam.lang.gleam.LocalCallExpr;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.PatternField;
import io.beam.lang.gleam.RemoteCallExpr;
import io.beam.lang.gleam.StringExpr;
import io.beam.lang.gleam.TupleExpr;
import io.beam.lang.gleam.UseExpr;
import io.beam.lang.gleam.Variable;
import io.beam.lang.gleam.VariablePattern;
import io.smithy.beam.core.BeamMemberNames;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class GleamStructureHelperDsl {

  private GleamStructureHelperDsl() {}

  static List<Function> structureHelpers(
      Model model, StructureShape structure, SymbolProvider sp, String typesModule) {
    List<Function> functions = new ArrayList<>();
    functions.add(toJson(model, structure, sp, typesModule));
    functions.add(decoder(model, structure, sp, typesModule));
    functions.add(jsonTo(structure, sp, typesModule));
    return functions;
  }

  private static Function toJson(
      Model model, StructureShape structure, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, structure);
    String ctor =
        GleamCodecNames.qualifiedConstructor(
            typesModule, GleamCodecNames.structureConstructor(sp, structure));
    List<PatternField> shorthand = new ArrayList<>();
    List<Expression> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = BeamMemberNames.fieldName(sp, member);
      shorthand.add(PatternField.shorthand(field));
      entries.add(
          TupleExpr.of(
              List.of(
                  StringExpr.of(GleamCodecNames.jsonKey(member)),
                  GleamJsonMemberCoding.encodeMemberValue(
                      model, structure, member, sp, Variable.of(field)))));
    }
    return Function.pub(
        GleamCodecNames.toJsonName(structure),
        List.of(Param.of("value", type)),
        "json.Json",
        BlockExpr.of(
            List.of(
                LetExpr.of(ConstructorPattern.of(ctor, shorthand), Variable.of("value")),
                RemoteCallExpr.of("json", "object", List.of(ListExpr.of(entries))))));
  }

  private static Function decoder(
      Model model, StructureShape structure, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, structure);
    String ctor =
        GleamCodecNames.qualifiedConstructor(
            typesModule, GleamCodecNames.structureConstructor(sp, structure));
    List<Expression> statements = new ArrayList<>();
    List<LabelledArg> ctorArgs = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = BeamMemberNames.fieldName(sp, member);
      statements.add(
          UseExpr.of(field, GleamJsonMemberCoding.memberDecoder(model, structure, member, sp)));
      ctorArgs.add(LabelledArg.labelled(field, Variable.of(field)));
    }
    statements.add(
        RemoteCallExpr.of(
            "decode", "success", List.of(ConstructorExpr.of(ctor, ctorArgs))));
    return Function.pub(
        GleamCodecNames.decoderName(structure),
        List.of(),
        "decode.Decoder(" + type + ")",
        BlockExpr.of(statements));
  }

  private static Function jsonTo(
      StructureShape structure, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, structure);
    return Function.pub(
        GleamCodecNames.jsonToName(structure),
        List.of(Param.of("data", "dynamic.Dynamic")),
        "Result(" + type + ", List(decode.DecodeError))",
        RemoteCallExpr.of(
            "decode",
            "run",
            List.of(
                Variable.of("data"),
                LocalCallExpr.of(GleamCodecNames.decoderName(structure), List.of()))));
  }
}
