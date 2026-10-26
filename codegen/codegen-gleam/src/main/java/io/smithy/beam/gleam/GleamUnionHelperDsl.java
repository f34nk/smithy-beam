package io.smithy.beam.gleam;

import io.beam.lang.gleam.CaseClause;
import io.beam.lang.gleam.CaseExpr;
import io.beam.lang.gleam.ConstructorPattern;
import io.beam.lang.gleam.Expression;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.ListExpr;
import io.beam.lang.gleam.LocalCallExpr;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.PatternField;
import io.beam.lang.gleam.RawExpr;
import io.beam.lang.gleam.RemoteCallExpr;
import io.beam.lang.gleam.StringExpr;
import io.beam.lang.gleam.TupleExpr;
import io.beam.lang.gleam.Variable;
import io.beam.lang.gleam.VariablePattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class GleamUnionHelperDsl {

  private GleamUnionHelperDsl() {}

  static List<Function> unionHelpers(
      Model model, UnionShape union, SymbolProvider sp, String typesModule) {
    List<Function> functions = new ArrayList<>();
    functions.add(toJson(model, union, sp, typesModule));
    functions.add(decoder(model, union, sp, typesModule));
    functions.add(jsonTo(union, sp, typesModule));
    return functions;
  }

  private static Function toJson(
      Model model, UnionShape union, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, union);
    String unknown = GleamCodecNames.unknownConstructor(sp, union);
    List<CaseClause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      String ctor = GleamCodecNames.constructorForMember(sp, union, member.getMemberName());
      String wireKey = member.getMemberName();
      Expression encoded =
          GleamJsonMemberCoding.encodeRequiredValue(model, member, sp, Variable.of("inner"));
      Expression object =
          RemoteCallExpr.of(
              "json",
              "object",
              List.of(
                  ListExpr.of(
                      List.of(TupleExpr.of(List.of(StringExpr.of(wireKey), encoded))))));
      clauses.add(
          CaseClause.of(
              ConstructorPattern.of(
                  ctor, List.of(PatternField.positional(VariablePattern.of("inner")))),
              object));
    }
    Expression unknownObject =
        RemoteCallExpr.of(
            "json",
            "object",
            List.of(
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(
                                Variable.of("tag"),
                                RemoteCallExpr.of("json", "null", List.of())))))));
    clauses.add(
        CaseClause.of(
            ConstructorPattern.of(
                unknown, List.of(PatternField.positional(VariablePattern.of("tag")))),
            unknownObject));
    return Function.pub(
        GleamCodecNames.toJsonName(union),
        List.of(Param.of("value", type)),
        "json.Json",
        CaseExpr.of(Variable.of("value"), clauses));
  }

  private static Function decoder(
      Model model, UnionShape union, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, union);
    return Function.pub(
        GleamCodecNames.decoderName(union),
        List.of(),
        "decode.Decoder(" + type + ")",
        RawExpr.of(unionDecoderSource(model, union, sp)));
  }

  private static String unionDecoderSource(Model model, UnionShape union, SymbolProvider sp) {
    String unknown = GleamCodecNames.unknownConstructor(sp, union);
    StringBuilder raw = new StringBuilder();
    raw.append("decode.dict(decode.string, decode.dynamic)\n");
    raw.append("  |> decode.then(fn(entries) {\n");
    raw.append("    case dict.to_list(entries) {\n");
    for (MemberShape member : union.members()) {
      String ctor = GleamCodecNames.constructorForMember(sp, union, member.getMemberName());
      String wireKey = member.getMemberName();
      String innerDecoder = GleamJsonMemberCoding.shapeDecoderSource(model, member, sp);
      raw.append("      [#(\"")
          .append(wireKey)
          .append("\", inner)] ->\n");
      raw.append("        case decode.run(inner, ")
          .append(innerDecoder)
          .append(") {\n");
      raw.append("          Ok(v) -> decode.success(")
          .append(ctor)
          .append("(v))\n");
      raw.append("          Error(_) -> decode.success(")
          .append(unknown)
          .append("(\"")
          .append(wireKey)
          .append("\"))\n");
      raw.append("        }\n");
    }
    raw.append("      [#(tag, _)] -> decode.success(")
        .append(unknown)
        .append("(tag))\n");
    raw.append("      _ -> decode.success(")
        .append(unknown)
        .append("(\"\"))\n");
    raw.append("    }\n");
    raw.append("  })");
    return raw.toString();
  }

  private static Function jsonTo(UnionShape union, SymbolProvider sp, String typesModule) {
    String type = GleamCodecNames.qualifiedType(typesModule, sp, union);
    return Function.pub(
        GleamCodecNames.jsonToName(union),
        List.of(Param.of("data", "dynamic.Dynamic")),
        "Result(" + type + ", List(decode.DecodeError))",
        RemoteCallExpr.of(
            "decode",
            "run",
            List.of(
                Variable.of("data"),
                LocalCallExpr.of(GleamCodecNames.decoderName(union), List.of()))));
  }
}
