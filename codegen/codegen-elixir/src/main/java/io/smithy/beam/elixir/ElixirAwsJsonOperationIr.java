package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExStructFieldPattern;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
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

  static ExFunction buildEncodeRequest(
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

    ExSpec spec = ExSpec.functionSpec("encode_" + opName + "_request", inputType, httpRequestType);
    ExStructPattern inputPattern = new ExStructPattern("Types." + inputStruct, List.of(), "input");

    List<ExExpr> body = new ArrayList<>();
    body.add(
        ElixirBeamIrBridge.expr(
            ElixirJsonCodecIr.rejectNilMapPipeline(
                "body_map",
                ElixirJsonCodecIr.bodyMapEntries(
                    model, httpIndex, sp, typesMod, members, "input", eventStreamModule))));
    body.add(
        ExMatch.match(
            ExVarPattern.var("body"), ExCall.call("Jason", "encode!", ExVar.var("body_map"))));
    body.add(buildAwsJsonHttpRequestExpr(runtimeMod, amzTarget, contentType));

    return ExFunction.functionWithDocAndSpec(
        "def",
        "encode_" + opName + "_request",
        ExDoc.doc("Encode AWS JSON request for " + op.getId() + "."),
        spec,
        List.of(ExClause.blockClause(List.of(inputPattern), body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildDecodeResponse(
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

    ExSpec spec =
        ExSpec.functionSpec(
            "decode_" + opName + "_response",
            "map()",
            "{:ok, " + outputType + "} | {:error, term()}");

    ExStructPattern successPattern =
        new ExStructPattern(
            runtimeMod + ".HttpResponse",
            List.of(
                ExStructFieldPattern.fieldPattern("status", ExIntegerPattern.integer(200)),
                ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))));

    ExStructPattern errorPattern =
        new ExStructPattern(
            runtimeMod + ".HttpResponse",
            List.of(
                ExStructFieldPattern.fieldPattern("status", ExVarPattern.var("status")),
                ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")),
                ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))));

    List<ExExpr> successBody = new ArrayList<>();
    if (ElixirJsonCodecIr.isEventStreamPayload(members, model)) {
      MemberShape member = members.get(0);
      UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
      String helper = ElixirEventStreamEmitter.helperName(sp, union);
      String fieldName = memberFieldName(sp, member);
      successBody.add(
          ExTuple.tuple(
              ExAtom.atom("ok"),
              ExStruct.struct(
                  "Types." + outputStruct,
                  ExMapEntry.entry(
                      ExAtom.atom(fieldName),
                      ExCall.call(eventStreamModule, "decode_" + helper, ExVar.var("body"))))));
    } else {
      successBody.addAll(
          ElixirJsonCodecIr.decodedBodyPrelude().stream().map(ElixirBeamIrBridge::statement).toList());
      successBody.add(
          ExTuple.tuple(
              ExAtom.atom("ok"),
              ExStruct.struct(
                  "Types." + outputStruct,
                  ElixirBeamIrBridge.mapEntries(
                          ElixirJsonCodecIr.structFieldEntriesFromDecoded(
                              model, httpIndex, sp, typesMod, members, eventStreamModule))
                      .toArray(ExMapEntry[]::new))));
    }

    return ExFunction.functionWithDocAndSpec(
        "def",
        "decode_" + opName + "_response",
        ExDoc.doc("Decode AWS JSON response for " + op.getId() + "."),
        spec,
        List.of(
            ExClause.blockClause(List.of(successPattern), successBody.toArray(ExExpr[]::new)),
            ExClause.clause(
                List.of(errorPattern),
                ExCallLocal.callLocal(
                    "decode_" + opName + "_response_error",
                    ExVar.var("status"),
                    ExVar.var("headers"),
                    ExVar.var("body")))));
  }

  static ExFunction buildDecodeRequest(
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

    ExSpec spec = ExSpec.functionSpec("decode_" + opName + "_request", "map()", inputType);
    ExStructPattern pattern =
        new ExStructPattern(
            runtimeMod + ".HttpRequest",
            List.of(ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))));

    List<ExExpr> body = new ArrayList<>();
    if (ElixirJsonCodecIr.isEventStreamPayload(members, model)) {
      body.add(
          ExStruct.struct(
              "Types." + inputStruct,
              ElixirBeamIrBridge.mapEntries(
                      ElixirJsonCodecIr.structFieldEntriesFromDecoded(
                          model, httpIndex, sp, typesMod, members, eventStreamModule))
                  .toArray(ExMapEntry[]::new)));
    } else {
      body.add(
          ExMatch.match(
              ExVarPattern.var("decoded"),
              ExCallLocal.callLocal("decode_json_body", ExVar.var("body"))));
      body.add(
          ExStruct.struct(
              "Types." + inputStruct,
              ElixirBeamIrBridge.mapEntries(
                      ElixirJsonCodecIr.structFieldEntriesFromDecoded(
                          model, httpIndex, sp, typesMod, members, eventStreamModule))
                  .toArray(ExMapEntry[]::new)));
    }

    return ExFunction.functionWithDocAndSpec(
        "def",
        "decode_" + opName + "_request",
        ExDoc.doc("Decode AWS JSON request for " + op.getId() + "."),
        spec,
        List.of(ExClause.blockClause(List.of(pattern), body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildEncodeResponse(
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

    ExSpec spec = ExSpec.functionSpec("encode_" + opName + "_response", outputType, "map()");
    ExStructPattern pattern = new ExStructPattern("Types." + outputStruct, List.of(), "output");

    List<ExExpr> body = new ArrayList<>();
    body.add(
        ElixirBeamIrBridge.expr(
            ElixirJsonCodecIr.rejectNilMapPipeline(
                "body_map",
                ElixirJsonCodecIr.bodyMapEntries(
                    model, httpIndex, sp, typesMod, members, "output", eventStreamModule))));
    body.add(
        ExMatch.match(
            ExVarPattern.var("body"), ExCall.call("Jason", "encode!", ExVar.var("body_map"))));
    body.add(
        ExMatch.match(
            ExVarPattern.var("headers"),
            ExList.list(
                ExTuple.tuple(ExString.string("Content-Type"), ExString.string(contentType)))));
    body.add(
        ExMap.map(
            ExMapEntry.entry(ExAtom.atom("status"), ExInteger.integer(200)),
            ExMapEntry.entry(ExAtom.atom("headers"), ExVar.var("headers")),
            ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body"))));

    return ExFunction.functionWithDocAndSpec(
        "def",
        "encode_" + opName + "_response",
        ExDoc.doc("Encode AWS JSON response for " + op.getId() + "."),
        spec,
        List.of(ExClause.blockClause(List.of(pattern), body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildErrorDispatch(
      Model model, OperationShape op, SymbolProvider sp, String typesMod) {
    return ElixirRestJsonOperationIr.buildErrorDispatch(model, op, sp, typesMod);
  }

  private static String memberFieldName(SymbolProvider sp, MemberShape member) {
    Symbol sym = sp.toSymbol(member);
    return sym.getProperty("fieldName", String.class)
        .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
  }

  private static ExStruct buildAwsJsonHttpRequestExpr(
      String runtimeMod, String amzTarget, String contentType) {
    return ExStruct.struct(
        runtimeMod + ".HttpRequest",
        ExMapEntry.entry(ExAtom.atom("method"), ExString.string("POST")),
        ExMapEntry.entry(ExAtom.atom("path"), ExString.string("/")),
        ExMapEntry.entry(ExAtom.atom("query"), ExMap.map()),
        ExMapEntry.entry(
            ExAtom.atom("headers"),
            ExList.list(
                ExTuple.tuple(ExString.string("Content-Type"), ExString.string(contentType)),
                ExTuple.tuple(ExString.string("X-Amz-Target"), ExString.string(amzTarget)))),
        ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body")));
  }
}
