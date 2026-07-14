package io.smithy.beam.elixir;

import io.beam.ir.elixir.AssignPattern;
import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.BlockExpr;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionDoc;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.IntegerPattern;
import io.beam.ir.elixir.ListExpr;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.MapEntry;
import io.beam.ir.elixir.MapExpr;
import io.beam.ir.elixir.MatchExpr;
import io.beam.ir.elixir.Pattern;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.Spec;
import io.beam.ir.elixir.StringExpr;
import io.beam.ir.elixir.StructExpr;
import io.beam.ir.elixir.StructField;
import io.beam.ir.elixir.StructPattern;
import io.beam.ir.elixir.StructPatternField;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class ElixirAwsJsonOperationIr {
  private ElixirAwsJsonOperationIr() {}

  static List<Function> buildEncodeRequest(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String targetPrefix,
      String contentType,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputStruct = sp.toSymbol(input).getName();
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";
    List<MemberShape> members = ElixirJsonCodecIr.documentMembers(httpIndex, op, input, true);
    String amzTarget = targetPrefix + "." + op.getId().getName();

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bind(
            "body_map",
            ElixirJsonCodecIr.rejectNilMapPipeline(
                "body_map",
                ElixirJsonCodecIr.bodyMapEntries(
                    model, httpIndex, sp, typesMod, members, "input", eventStreamModule))));
    body.add(
        MatchExpr.bind(
            "body",
            RemoteCallExpr.of("Jason", "encode!", List.of(Variable.of("body_map")))));
    body.add(buildAwsJsonHttpRequestExpr(runtimeMod, amzTarget, contentType));

    Spec spec = Spec.of("encode_" + opName + "_request(" + inputType + ") -> " + httpRequestType);

    return List.of(
        def(
            "encode_" + opName + "_request",
            List.of(
                AssignPattern.of(
                    "input", StructPattern.of("Types." + inputStruct, List.of()))),
            block(body),
            spec,
            FunctionDoc.of("Encode AWS JSON request for " + op.getId() + "."),
            false));
  }

  static List<Function> buildDecodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputStruct = sp.toSymbol(output).getName();
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
    List<MemberShape> members = ElixirJsonCodecIr.documentMembers(httpIndex, op, output, false);

    Spec spec =
        Spec.of(
            "decode_"
                + opName
                + "_response(map()) -> {:ok, "
                + outputType
                + "} | {:error, term()}");

    Pattern successPattern =
        StructPattern.of(
            runtimeMod + ".HttpResponse",
            List.of(
                StructPatternField.of("status", IntegerPattern.of(200)),
                StructPatternField.of("body", VariablePattern.of("body"))));

    Pattern errorPattern =
        StructPattern.of(
            runtimeMod + ".HttpResponse",
            List.of(
                StructPatternField.of("status", VariablePattern.of("status")),
                StructPatternField.of("headers", VariablePattern.of("headers")),
                StructPatternField.of("body", VariablePattern.of("body"))));

    List<Expression> successBody = new ArrayList<>();
    if (ElixirJsonCodecIr.isEventStreamPayload(members, model)) {
      MemberShape member = members.get(0);
      UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
      String helper = ElixirEventStreamEmitter.helperName(sp, union);
      String fieldName = memberFieldName(sp, member);
      successBody.add(
          TupleExpr.of(
              List.of(
                  AtomExpr.of("ok"),
                  StructExpr.of(
                      "Types." + outputStruct,
                      List.of(
                          StructField.of(
                              fieldName,
                              RemoteCallExpr.of(
                                  eventStreamModule,
                                  "decode_" + helper,
                                  List.of(Variable.of("body")))))))));
    } else {
      successBody.addAll(ElixirJsonCodecIr.decodedBodyPrelude());
      successBody.add(
          TupleExpr.of(
              List.of(
                  AtomExpr.of("ok"),
                  StructExpr.of(
                      "Types." + outputStruct,
                      structFields(
                          ElixirJsonCodecIr.structFieldEntriesFromDecoded(
                              model, httpIndex, sp, typesMod, members, eventStreamModule))))));
    }

    return List.of(
        def(
            "decode_" + opName + "_response",
            List.of(successPattern),
            block(successBody),
            spec,
            FunctionDoc.of("Decode AWS JSON response for " + op.getId() + "."),
            false),
        def(
            "decode_" + opName + "_response",
            List.of(errorPattern),
            LocalCallExpr.of(
                "decode_" + opName + "_response_error",
                List.of(
                    Variable.of("status"),
                    Variable.of("headers"),
                    Variable.of("body"))),
            null,
            null,
            true));
  }

  static List<Function> buildDecodeRequest(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputStruct = sp.toSymbol(input).getName();
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    List<MemberShape> members = ElixirJsonCodecIr.documentMembers(httpIndex, op, input, true);

    Spec spec = Spec.of("decode_" + opName + "_request(map()) -> " + inputType);

    Pattern pattern =
        StructPattern.of(
            runtimeMod + ".HttpRequest",
            List.of(StructPatternField.of("body", VariablePattern.of("body"))));

    List<Expression> body = new ArrayList<>();
    if (ElixirJsonCodecIr.isEventStreamPayload(members, model)) {
      body.add(
          StructExpr.of(
              "Types." + inputStruct,
              structFields(
                  ElixirJsonCodecIr.structFieldEntriesFromDecoded(
                      model, httpIndex, sp, typesMod, members, eventStreamModule))));
    } else {
      body.add(
          MatchExpr.bind(
              "decoded", LocalCallExpr.of("decode_json_body", List.of(Variable.of("body")))));
      body.add(
          StructExpr.of(
              "Types." + inputStruct,
              structFields(
                  ElixirJsonCodecIr.structFieldEntriesFromDecoded(
                      model, httpIndex, sp, typesMod, members, eventStreamModule))));
    }

    return List.of(
        def(
            "decode_" + opName + "_request",
            List.of(pattern),
            block(body),
            spec,
            FunctionDoc.of("Decode AWS JSON request for " + op.getId() + "."),
            false));
  }

  static List<Function> buildEncodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String contentType,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputStruct = sp.toSymbol(output).getName();
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
    List<MemberShape> members = ElixirJsonCodecIr.documentMembers(httpIndex, op, output, false);

    Spec spec = Spec.of("encode_" + opName + "_response(" + outputType + ") -> map()");

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bind(
            "body_map",
            ElixirJsonCodecIr.rejectNilMapPipeline(
                "body_map",
                ElixirJsonCodecIr.bodyMapEntries(
                    model, httpIndex, sp, typesMod, members, "output", eventStreamModule))));
    body.add(
        MatchExpr.bind(
            "body",
            RemoteCallExpr.of("Jason", "encode!", List.of(Variable.of("body_map")))));
    body.add(
        MatchExpr.bind(
            "headers",
            ListExpr.of(
                List.of(
                    TupleExpr.of(
                        List.of(
                            StringExpr.of("Content-Type"), StringExpr.of(contentType)))))));
    body.add(
        MapExpr.of(
            List.of(
                MapEntry.atomKey("status", io.beam.ir.elixir.IntegerExpr.of(200)),
                MapEntry.atomKey("headers", Variable.of("headers")),
                MapEntry.atomKey("body", Variable.of("body")))));

    return List.of(
        def(
            "encode_" + opName + "_response",
            List.of(
                AssignPattern.of(
                    "output", StructPattern.of("Types." + outputStruct, List.of()))),
            block(body),
            spec,
            FunctionDoc.of("Encode AWS JSON response for " + op.getId() + "."),
            false));
  }

  static List<Function> buildErrorDispatch(
      Model model, OperationShape op, SymbolProvider sp, String typesMod) {
    return ElixirRestJsonOperationIr.buildErrorDispatch(model, op, sp, typesMod);
  }

  private static String memberFieldName(SymbolProvider sp, MemberShape member) {
    Symbol sym = sp.toSymbol(member);
    return sym.getProperty("fieldName", String.class)
        .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
  }

  private static Expression buildAwsJsonHttpRequestExpr(
      String runtimeMod, String amzTarget, String contentType) {
    return StructExpr.of(
        runtimeMod + ".HttpRequest",
        List.of(
            StructField.of("method", StringExpr.of("POST")),
            StructField.of("path", StringExpr.of("/")),
            StructField.of("query", MapExpr.of(List.of())),
            StructField.of(
                "headers",
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(
                                StringExpr.of("Content-Type"), StringExpr.of(contentType))),
                        TupleExpr.of(
                            List.of(
                                StringExpr.of("X-Amz-Target"), StringExpr.of(amzTarget)))))),
            StructField.of("body", Variable.of("body"))));
  }

  private static List<StructField> structFields(List<MapEntry> entries) {
    List<StructField> fields = new ArrayList<>();
    for (MapEntry entry : entries) {
      String name =
          entry.key() instanceof AtomExpr atom
              ? atom.value()
              : ((StringExpr) entry.key()).value();
      fields.add(StructField.of(name, entry.value()));
    }
    return fields;
  }

  private static Function def(
      String name,
      List<Pattern> params,
      Expression body,
      Spec spec,
      FunctionDoc doc,
      boolean oneLiner) {
    return new Function(name, false, List.of(FunctionHead.of(params)), body, spec, doc, oneLiner);
  }

  private static Expression block(List<Expression> statements) {
    if (statements.isEmpty()) {
      return io.beam.ir.elixir.NilExpr.of();
    }
    if (statements.size() == 1) {
      return statements.get(0);
    }
    return new BlockExpr(statements);
  }
}
