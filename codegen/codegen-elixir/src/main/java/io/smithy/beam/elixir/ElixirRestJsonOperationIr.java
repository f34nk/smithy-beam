package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamRequestCompressionIndex;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIfInList;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPattern;
import io.smithy.beam.ir.elixir.ExPipeCase;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExStructFieldPattern;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExMapUpdate;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import io.smithy.beam.ir.elixir.ExWith;
import io.smithy.beam.ir.elixir.ExWithClause;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

final class ElixirRestJsonOperationIr {
  private ElixirRestJsonOperationIr() {}

  static ExFunction buildEncodeRequest(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean encodeWithConfig,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
    String method = httpTrait.getMethod();
    String uriTemplate = httpTrait.getUri().toString();

    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    boolean hasHostLabels =
        !hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class);

    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
    List<HttpBinding> queryParams =
        httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
    List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> prefixHeaders =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> reqPayload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

    boolean streamingRequestPayload = hasStreamingRequestPayload(model, reqPayload, method);
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";

    List<ExPattern> patterns =
        encodeWithConfig
            ? List.of(ExVarPattern.var("config"), ExVarPattern.var("input"))
            : List.of(ExVarPattern.var("input"));

    List<ExExpr> body = new ArrayList<>();
    body.addAll(buildIdempotencyTokenExprs(input, typesMod, sp));
    body.add(
        ExMatch.match(
            ExVarPattern.var("path"),
            buildPathExpression(uriTemplate, labels, sp, "input")));
    body.addAll(buildQueryExprs(queries, sp));
    body.addAll(buildQueryParamsExprs(queryParams, sp));
    body.addAll(buildRequestHeadersExprs(model, op, headers, prefixHeaders, sp, "input"));
    body.addAll(
        buildRequestBodyExprs(
            model, httpIndex, reqPayload, docMembers, method, sp, "input", eventStreamModule));
    ElixirHttpChecksumIr.requestChecksumHeadersExpr(model, op, sp, "headers").ifPresent(body::add);
    body.addAll(buildRequestCompressionExprs(op));

    if (streamingRequestPayload) {
      HttpBinding payload = reqPayload.get(0);
      String field = fieldName(sp, payload.getMember());
      body.add(
          ExMatch.match(
              ExVarPattern.var("stream"),
              ExStructAccess.structAccess(ExVar.var("input"), field)));
      body.add(ExMatch.match(ExVarPattern.var("body"), ExString.string("")));
    }

    if (hasHostLabels) {
      body.add(
          ExMatch.match(
              ExVarPattern.var("host"),
              ExCallLocal.callLocal("build_host", ExVar.var("input"), ExVar.var("config"))));
    }

    body.add(
        buildHttpRequestStruct(
            runtimeMod, method, streamingRequestPayload, hasHostLabels));

    ExSpec spec =
        encodeWithConfig
            ? ExSpec.functionSpec(
                "encode_" + opName + "_request", "map(), " + inputType, httpRequestType)
            : ExSpec.functionSpec("encode_" + opName + "_request", inputType, httpRequestType);

    return ExFunction.functionWithSpec(
        "def",
        "encode_" + opName + "_request",
        spec,
        List.of(
            encodeWithConfig
                ? ExClause.blockClauseSingleLineHead(patterns, body.toArray(ExExpr[]::new))
                : ExClause.blockClause(patterns, body.toArray(ExExpr[]::new))));
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

    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
    List<HttpBinding> queryParams =
        httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
    List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> prefixHeaders =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> reqPayload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
    boolean streamingRequestPayload = hasStreamingRequestPayload(model, reqPayload, null);

    List<ExStructFieldPattern> requestFields = new ArrayList<>();
    requestFields.add(ExStructFieldPattern.fieldPattern("query", ExVarPattern.var("query")));
    requestFields.add(ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")));
    requestFields.add(ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body")));
    if (streamingRequestPayload) {
      requestFields.add(ExStructFieldPattern.fieldPattern("stream", ExVarPattern.var("stream")));
    }

    List<ExPattern> patterns = new ArrayList<>();
    patterns.add(ExStructPattern.struct(runtimeMod + ".HttpRequest", requestFields));
    if (!labels.isEmpty()) {
      patterns.add(ExVarPattern.var("label_map"));
    }

    List<ExExpr> body = new ArrayList<>();
    if (!docMembers.isEmpty()) {
      body.addAll(ElixirJsonCodecIr.decodedBodyPrelude());
    }
    body.add(
        buildInputStruct(
            typesMod,
            inputStruct,
            model,
            httpIndex,
            sp,
            eventStreamModule,
            labels,
            queries,
            queryParams,
            headers,
            prefixHeaders,
            docMembers,
            reqPayload,
            streamingRequestPayload));

    String returnType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    ExSpec spec =
        labels.isEmpty()
            ? ExSpec.functionSpec(
                "decode_" + opName + "_request", "%" + runtimeMod + ".HttpRequest{}", returnType)
            : ExSpec.functionSpec(
                "decode_" + opName + "_request",
                "%" + runtimeMod + ".HttpRequest{}, map()",
                returnType);

    return ExFunction.functionWithDocAndSpec(
        "def",
        "decode_" + opName + "_request",
        ExDoc.doc("Decode HTTP request for " + op.getId() + "."),
        spec,
        List.of(ExClause.blockClause(patterns, body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildDecodeResponse(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputStruct = sp.toSymbol(output).getName();
    int successCode = httpIndex.getResponseCode(op);

    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    List<HttpBinding> respCode =
        httpIndex.getResponseBindings(op, HttpBinding.Location.RESPONSE_CODE);
    boolean streamingResponsePayload =
        !respPayload.isEmpty() && isStreamingBlob(model, respPayload.get(0).getMember());

    List<ExStructFieldPattern> successFields = new ArrayList<>();
    if (!respCode.isEmpty()) {
      successFields.add(
          ExStructFieldPattern.fieldPattern("status", ExVarPattern.var("http_status")));
    } else {
      successFields.add(
          ExStructFieldPattern.fieldPattern("status", ExIntegerPattern.integer(successCode)));
    }
    successFields.add(
        ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")));
    successFields.add(ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body")));
    if (streamingResponsePayload) {
      successFields.add(
          ExStructFieldPattern.fieldPattern("stream", ExVarPattern.var("stream")));
    }

    List<ExGuard> successGuards = new ArrayList<>();
    if (!respCode.isEmpty()) {
      successGuards.add(
          ExGuard.exprGuard(
              ExOp.op(">=", ExVar.var("http_status"), ExInteger.integer(200))));
      successGuards.add(
          ExGuard.exprGuard(
              ExOp.op("<", ExVar.var("http_status"), ExInteger.integer(300))));
    }

    List<ExExpr> successBody = new ArrayList<>();
    boolean needsContentTypeCheck = responsePayloadRequiresContentTypeCheck(model, respPayload);
    if (needsContentTypeCheck) {
      String expectedContentType = resolvedResponseContentType(model, op);
      successBody.add(
          ExWith.withExpr(
              List.of(
                  ExWithClause.clause(
                      ExAtomPattern.atom("ok"),
                      ExCallLocal.callLocal(
                          "content_type_matches",
                          ExVar.var("headers"),
                          ExString.string(expectedContentType)))),
              buildDecodeResponseSuccessBody(
                  model,
                  op,
                  httpIndex,
                  sp,
                  typesMod,
                  outputStruct,
                  respHeaders,
                  respPrefixHeaders,
                  respDoc,
                  respPayload,
                  respCode,
                  streamingResponsePayload).toArray(ExExpr[]::new)));
    } else {
      successBody.addAll(
          buildDecodeResponseSuccessBody(
              model,
              op,
              httpIndex,
              sp,
              typesMod,
              outputStruct,
              respHeaders,
              respPrefixHeaders,
              respDoc,
              respPayload,
              respCode,
              streamingResponsePayload));
    }

    ExClause successClause =
        successGuards.isEmpty()
            ? ExClause.blockClause(
                List.of(ExStructPattern.struct(runtimeMod + ".HttpResponse", successFields)),
                successBody.toArray(ExExpr[]::new))
            : new ExClause(
                List.of(ExStructPattern.struct(runtimeMod + ".HttpResponse", successFields)),
                successGuards,
                successBody,
                false,
                true);

    ExClause errorClause =
        ExClause.clause(
            List.of(
                ExStructPattern.struct(
                    runtimeMod + ".HttpResponse",
                    List.of(
                        ExStructFieldPattern.fieldPattern("status", ExVarPattern.var("status")),
                        ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")),
                        ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))))),
            ExCallLocal.callLocal(
                "decode_" + opName + "_response_error",
                ExVar.var("status"),
                ExVar.var("headers"),
                ExVar.var("body")));

    return ExFunction.defFunction(
        "decode_" + opName + "_response", List.of(successClause, errorClause));
  }

  static ExFunction buildErrorDispatch(
      Model model, OperationShape op, SymbolProvider sp, String typesMod) {
    String opName = sp.toSymbol(op).getName();
    List<ShapeId> errors = new ArrayList<>(op.getErrors());

    List<ExClause> clauses = new ArrayList<>();

    for (ShapeId errorId : errors) {
      StructureShape errShape = model.expectShape(errorId, StructureShape.class);
      int httpStatus =
          errShape.hasTrait(HttpErrorTrait.class)
              ? errShape.expectTrait(HttpErrorTrait.class).getCode()
              : -1;
      if (httpStatus <= 0) {
        continue;
      }
      String modName = sp.toSymbol(errShape).getName();
      clauses.add(
          ExClause.blockClauseSingleLineHead(
              List.of(
                  ExIntegerPattern.integer(httpStatus),
                  ExVarPattern.var("_headers"),
                  ExVarPattern.var("body")),
              ExMatch.match(
                  ExVarPattern.var("decoded"),
                  ExCallLocal.callLocal("decode_json_body", ExVar.var("body"))),
              buildErrorTuple(typesMod, modName, model, errShape, sp)));
    }

    boolean hasTypeDiscriminated =
        errors.stream()
            .anyMatch(
                e -> !model.expectShape(e, StructureShape.class).hasTrait(HttpErrorTrait.class));

    if (hasTypeDiscriminated) {
      List<ExCaseBranch> typeBranches = new ArrayList<>();
      for (ShapeId errorId : errors) {
        StructureShape errShape = model.expectShape(errorId, StructureShape.class);
        if (errShape.hasTrait(HttpErrorTrait.class)) {
          continue;
        }
        String modName = sp.toSymbol(errShape).getName();
        typeBranches.add(
            ExCaseBranch.branch(
                ExStringPattern.string(errorId.getName()),
                buildErrorTuple(typesMod, modName, model, errShape, sp)));
      }
      typeBranches.add(
          ExCaseBranch.branch(
              ExVarPattern.var("_"),
              ExTuple.tuple(
                  ExAtom.atom("error"),
                  ExTuple.tuple(
                      ExAtom.atom("unknown_error"),
                      ExVar.var("status"),
                      ExVar.var("body")))));

      clauses.add(
          new ExClause(
              List.of(
                  ExVarPattern.var("status"),
                  ExVarPattern.var("_headers"),
                  ExVarPattern.var("body")),
              List.of(ExGuard.exprGuard(ExOp.op(">=", ExVar.var("status"), ExInteger.integer(400)))),
              List.of(
                  ExMatch.match(
                      ExVarPattern.var("decoded"),
                      ExCallLocal.callLocal("decode_json_body", ExVar.var("body"))),
                  ExMatch.match(
                      ExVarPattern.var("error_type"),
                      ExCall.call(
                          "Map",
                          "get",
                          ExVar.var("decoded"),
                          ExString.string("__type"))),
                  ExCase.caseExpr(
                      ExVar.var("error_type"), typeBranches.toArray(ExCaseBranch[]::new))),
              false,
              true));
    } else {
      clauses.add(
          ExClause.blockClauseSingleLineHead(
              List.of(
                  ExVarPattern.var("status"),
                  ExVarPattern.var("_headers"),
                  ExVarPattern.var("body")),
              ExTuple.tuple(
                  ExAtom.atom("error"),
                  ExTuple.tuple(
                      ExAtom.atom("unknown_error"),
                      ExVar.var("status"),
                      ExVar.var("body")))));
    }

    return ExFunction.defpFunction("decode_" + opName + "_response_error", clauses);
  }

  static ExFunction buildEncodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputStruct = sp.toSymbol(output).getName();
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));

    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

    List<ExStructFieldPattern> patternFields = new ArrayList<>();
    for (HttpBinding binding : concat(respHeaders, respPrefixHeaders, respDoc, respPayload)) {
      String field = fieldName(sp, binding.getMember());
      patternFields.add(
          ExStructFieldPattern.fieldPattern(field, ExVarPattern.var(field)));
    }

    return ExFunction.functionWithDocAndSpec(
        "def",
        "encode_" + opName + "_response",
        ExDoc.doc("Encode response for " + op.getId() + "."),
        ExSpec.functionSpec("encode_" + opName + "_response", outputType, "map()"),
        List.of(
            ExClause.blockClause(
                List.of(
                    new ExStructPattern("Types." + outputStruct, patternFields, "output")),
                buildEncodeResponseBodyExprs(model, op, httpIndex, sp, "output").toArray(ExExpr[]::new))));
  }

  static ExFunction buildErrorResponseEncoder(
      Model model, ShapeId errorId, SymbolProvider sp, String typesMod) {
    StructureShape errShape = model.expectShape(errorId, StructureShape.class);
    String modName = sp.toSymbol(errShape).getName();
    int status =
        errShape.hasTrait(HttpErrorTrait.class)
            ? errShape.expectTrait(HttpErrorTrait.class).getCode()
            : 500;

    List<ExStructFieldPattern> patternFields = new ArrayList<>();
    List<ExMapEntry> bodyEntries = new ArrayList<>();
    bodyEntries.add(
        ExMapEntry.entry(ExString.string("__type"), ExString.string(errorId.getName())));
    for (MemberShape member : errShape.members()) {
      if (member.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = fieldName(sp, member);
      patternFields.add(ExStructFieldPattern.fieldPattern(field, ExVarPattern.var(field)));
      bodyEntries.add(
          ExMapEntry.entry(
              ExString.string(member.getMemberName()),
              ExStructAccess.structAccess(ExVar.var("error"), field)));
    }

    List<ExExpr> body = new ArrayList<>();
    body.add(ElixirJsonCodecIr.rejectNilMapPipeline("body_map", bodyEntries));
    body.add(
        ExMatch.match(
            ExVarPattern.var("body"),
            ExCall.call("Jason", "encode!", ExVar.var("body_map"))));
    body.add(
        ExMatch.match(
            ExVarPattern.var("headers"),
            ExList.list(
                ExTuple.tuple(
                    ExString.string("Content-Type"), ExString.string("application/json")))));
    body.add(
        ExMap.map(
            ExMapEntry.entry(ExAtom.atom("status"), ExInteger.integer(status)),
            ExMapEntry.entry(ExAtom.atom("headers"), ExVar.var("headers")),
            ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body"))));

    String errorType = typesMod + "." + modName + ".t()";
    return ExFunction.functionWithDocAndSpec(
        "def",
        "encode_" + modName + "_response",
        ExDoc.doc("Encode HTTP error response for " + errorId + "."),
        ExSpec.functionSpec("encode_" + modName + "_response", errorType, "map()"),
        List.of(
            ExClause.blockClause(
                List.of(new ExStructPattern("Types." + modName, patternFields, "error")),
                body.toArray(ExExpr[]::new))));
  }

  private static List<ExExpr> buildDecodeResponseSuccessBody(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String outputStruct,
      List<HttpBinding> respHeaders,
      List<HttpBinding> respPrefixHeaders,
      List<HttpBinding> respDoc,
      List<HttpBinding> respPayload,
      List<HttpBinding> respCode,
      boolean streamingResponsePayload) {
    List<ExExpr> body = new ArrayList<>();
    if (!respDoc.isEmpty()) {
      body.addAll(ElixirJsonCodecIr.decodedBodyPrelude());
    }

    for (HttpBinding hb : respHeaders) {
      String field = fieldName(sp, hb.getMember());
      body.add(
          ExMatch.match(
              ExVarPattern.var(field),
              headerValuePipeCase("headers", hb.getLocationName())));
    }

    List<ExMapEntry> structFields = new ArrayList<>();
    for (HttpBinding hb : respHeaders) {
      String field = fieldName(sp, hb.getMember());
      structFields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var(field)));
    }
    for (HttpBinding ph : respPrefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      structFields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              ExCallLocal.callLocal(
                  "prefix_headers_from_list",
                  ExVar.var("headers"),
                  ExString.string(ph.getLocationName()))));
    }
    for (HttpBinding db : respDoc) {
      String field = fieldName(sp, db.getMember());
      structFields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              decodeDocumentFieldExpr(model, sp, httpIndex, db.getMember())));
    }
    for (HttpBinding pb : respPayload) {
      String field = fieldName(sp, pb.getMember());
      if (isStreamingBlob(model, pb.getMember())) {
        structFields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var("stream")));
      } else {
        structFields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var("body")));
      }
    }
    for (HttpBinding rcb : respCode) {
      String field = fieldName(sp, rcb.getMember());
      structFields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var("http_status")));
    }

    ExExpr success =
        ExTuple.tuple(
            ExAtom.atom("ok"),
            ExStruct.struct("Types." + outputStruct, structFields));
    body.add(ExMatch.match(ExVarPattern.var("result"), success));
    body.add(ElixirHttpChecksumIr.responseChecksumGuardExpr(model, op, ExVar.var("result")));
    return body;
  }

  private static ExStruct buildInputStruct(
      String typesMod,
      String inputStruct,
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String eventStreamModule,
      List<HttpBinding> labels,
      List<HttpBinding> queries,
      List<HttpBinding> queryParams,
      List<HttpBinding> headers,
      List<HttpBinding> prefixHeaders,
      List<HttpBinding> docMembers,
      List<HttpBinding> reqPayload,
      boolean streamingRequestPayload) {
    List<ExMapEntry> fields = new ArrayList<>();
    for (HttpBinding lb : labels) {
      String field = fieldName(sp, lb.getMember());
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              ExCallLocal.callLocal(
                  "uri_decode",
                  ExCall.call(
                      "Map",
                      "get",
                      ExVar.var("label_map"),
                      ExString.string(lb.getMember().getMemberName())))));
    }
    for (HttpBinding qb : queries) {
      String field = fieldName(sp, qb.getMember());
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              ExCallLocal.callLocal(
                  "decode_query_param",
                  ExCall.call(
                      "Map",
                      "get",
                      ExVar.var("query"),
                      ExString.string(qb.getLocationName())))));
    }
    for (HttpBinding qp : queryParams) {
      String field = fieldName(sp, qp.getMember());
      fields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var("query")));
    }
    for (HttpBinding hb : headers) {
      String field = fieldName(sp, hb.getMember());
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(field), headerValuePipeCase("headers", hb.getLocationName())));
    }
    for (HttpBinding ph : prefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              ExCallLocal.callLocal(
                  "prefix_headers_from_list",
                  ExVar.var("headers"),
                  ExString.string(ph.getLocationName()))));
    }
    for (HttpBinding db : docMembers) {
      String field = fieldName(sp, db.getMember());
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              decodeDocumentFieldExpr(model, sp, httpIndex, db.getMember())));
    }
    for (HttpBinding pb : reqPayload) {
      String field = fieldName(sp, pb.getMember());
      if (isStreamingBlob(model, pb.getMember())) {
        fields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var("stream")));
      } else if (BeamEventStreamIndex.of(model).isEventStreamMember(pb.getMember())) {
        UnionShape union = model.expectShape(pb.getMember().getTarget(), UnionShape.class);
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(field),
                ExCall.call(eventStreamModule, "decode_" + helper, ExVar.var("body"))));
      } else {
        fields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var("body")));
      }
    }
    return ExStruct.struct("Types." + inputStruct, fields);
  }

  private static List<ExExpr> buildEncodeResponseBodyExprs(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String recordVar) {
    int successCode = httpIndex.getResponseCode(op);
    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    String responseContentType = resolvedResponseContentType(model, op);

    List<ExExpr> body = new ArrayList<>();
    if (!respPayload.isEmpty()) {
      HttpBinding pb = respPayload.get(0);
      String field = fieldName(sp, pb.getMember());
      if (isStreamingBlob(model, pb.getMember())) {
        body.add(
            ExMatch.match(
                ExVarPattern.var("stream"),
                ExStructAccess.structAccess(ExVar.var(recordVar), field)));
        body.add(ExMatch.match(ExVarPattern.var("body"), ExString.string("")));
      } else {
        body.add(
            ExMatch.match(
                ExVarPattern.var("body"),
                ExStructAccess.structAccess(ExVar.var(recordVar), field)));
      }
    } else if (!respDoc.isEmpty()) {
      List<MemberShape> docMemberShapes = respDoc.stream().map(HttpBinding::getMember).toList();
      List<ExMapEntry> entries =
          ElixirJsonCodecIr.bodyMapEntries(
              model,
              httpIndex,
              sp,
              "Types",
              docMemberShapes,
              recordVar,
              "event_stream");
      body.add(ElixirJsonCodecIr.rejectNilMapPipeline("body_map", entries));
      body.add(
          ExMatch.match(
              ExVarPattern.var("body"),
              ExCall.call("Jason", "encode!", ExVar.var("body_map"))));
    } else {
      body.add(ExMatch.match(ExVarPattern.var("body"), ExString.string("")));
    }

    if (!respHeaders.isEmpty()) {
      body.addAll(extraHeadersPipeline(sp, recordVar, respHeaders));
      body.add(
          ExMatch.match(
              ExVarPattern.var("headers"),
              ExList.cons(
                  ExTuple.tuple(
                      ExString.string("Content-Type"),
                      ExString.string(responseContentType)),
                  ExVar.var("extra_headers"))));
    } else {
      body.add(
          ExMatch.match(
              ExVarPattern.var("headers"),
              ExList.list(
                  ExTuple.tuple(
                      ExString.string("Content-Type"),
                      ExString.string(responseContentType)))));
    }

    for (HttpBinding ph : respPrefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      body.add(
          ExMatch.match(
              ExVarPattern.var("headers"),
              ExOp.op(
                  "++",
                  ExVar.var("headers"),
                  ExCallLocal.callLocal(
                      "prefix_headers_to_list",
                      ExString.string(ph.getLocationName()),
                      ExStructAccess.structAccess(ExVar.var(recordVar), field)))));
    }

    List<ExMapEntry> responseFields = new ArrayList<>();
    responseFields.add(ExMapEntry.entry(ExAtom.atom("status"), ExInteger.integer(successCode)));
    responseFields.add(ExMapEntry.entry(ExAtom.atom("headers"), ExVar.var("headers")));
    responseFields.add(ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body")));
    if (!respPayload.isEmpty() && isStreamingBlob(model, respPayload.get(0).getMember())) {
      responseFields.add(ExMapEntry.entry(ExAtom.atom("stream"), ExVar.var("stream")));
    }
    body.add(ExMap.map(responseFields.toArray(ExMapEntry[]::new)));
    return body;
  }

  private static List<ExExpr> buildIdempotencyTokenExprs(
      StructureShape input, String typesMod, SymbolProvider sp) {
    List<MemberShape> idempotencyMembers =
        input.members().stream().filter(m -> m.hasTrait(IdempotencyTokenTrait.class)).toList();
    if (idempotencyMembers.isEmpty()) {
      return List.of();
    }
    List<ExExpr> exprs = new ArrayList<>();
    for (MemberShape member : idempotencyMembers) {
      String field = fieldName(sp, member);
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("input"),
              ExCase.caseExpr(
                  ExStructAccess.structAccess(ExVar.var("input"), field),
                  ExCaseBranch.branch(
                      ExNilPattern.nil(),
                      ExMapUpdate.mapUpdate(
                          ExVar.var("input"),
                          ExMapEntry.entry(
                              ExAtom.atom(field), ExCallLocal.callLocal("generate_uuid")))),
                  ExCaseBranch.branch(ExVarPattern.var("_"), ExVar.var("input")))));
    }
    return exprs;
  }

  private static List<ExExpr> buildQueryExprs(List<HttpBinding> queries, SymbolProvider sp) {
    if (queries.isEmpty()) {
      return List.of(ExMatch.match(ExVarPattern.var("query"), ExMap.map()));
    }
    List<ExMapEntry> entries = new ArrayList<>();
    for (HttpBinding qb : queries) {
      String field = fieldName(sp, qb.getMember());
      entries.add(
          ExMapEntry.entry(
              ExString.string(qb.getLocationName()),
              ExStructAccess.structAccess(ExVar.var("input"), field)));
    }
    return List.of(ElixirJsonCodecIr.rejectNilMapPipeline("query", entries));
  }

  private static List<ExExpr> buildQueryParamsExprs(
      List<HttpBinding> queryParams, SymbolProvider sp) {
    if (queryParams.isEmpty()) {
      return List.of();
    }
    List<ExExpr> exprs = new ArrayList<>();
    for (HttpBinding qp : queryParams) {
      String field = fieldName(sp, qp.getMember());
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("query_extra"),
              ExCase.caseExpr(
                  ExStructAccess.structAccess(ExVar.var("input"), field),
                  ExCaseBranch.branch(ExNilPattern.nil(), ExList.list()),
                  ExCaseBranch.branch(
                      ExVarPattern.var("m"),
                      List.of(ExGuard.guard("is_map", ExVar.var("m"))),
                      ExCall.call("Map", "to_list", ExVar.var("m"))))));
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("query"),
              ExPipeline.pipeline(
                  "query",
                  ExVar.var("query"),
                  ExCall.call("Map", "to_list"),
                  ExCall.call("Enum", "concat", ExVar.var("query_extra")),
                  ExCall.call("Map", "new"))));
    }
    return exprs;
  }

  private static List<ExExpr> buildRequestHeadersExprs(
      Model model,
      OperationShape op,
      List<HttpBinding> headers,
      List<HttpBinding> prefixHeaders,
      SymbolProvider sp,
      String recordVar) {
    String requestContentType = resolvedRequestContentType(model, op);
    List<ExExpr> exprs = new ArrayList<>();
    if (headers.isEmpty()) {
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("headers"),
              ExList.list(
                  ExTuple.tuple(
                      ExString.string("Content-Type"),
                      ExString.string(requestContentType)))));
    } else {
      exprs.addAll(extraHeadersPipeline(sp, recordVar, headers));
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("headers"),
              ExList.cons(
                  ExTuple.tuple(
                      ExString.string("Content-Type"),
                      ExString.string(requestContentType)),
                  ExVar.var("extra_headers"))));
    }
    for (HttpBinding ph : prefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("headers"),
              ExOp.op(
                  "++",
                  ExVar.var("headers"),
                  ExCallLocal.callLocal(
                      "prefix_headers_to_list",
                      ExString.string(ph.getLocationName()),
                      ExStructAccess.structAccess(ExVar.var(recordVar), field)))));
    }
    return exprs;
  }

  private static List<ExExpr> extraHeadersPipeline(
      SymbolProvider sp, String recordVar, List<HttpBinding> headers) {
    List<ExExpr> entries = new ArrayList<>();
    for (HttpBinding hb : headers) {
      String field = fieldName(sp, hb.getMember());
      entries.add(
          ExIfInList.ifInList(
              ExOp.op(
                  "!=",
                  ExStructAccess.structAccess(ExVar.var(recordVar), field),
                  ExAtom.atom("nil")),
              ExTuple.tuple(
                  ExString.string(hb.getLocationName()),
                  ExCall.call(
                      "Kernel",
                      "to_string",
                      ExStructAccess.structAccess(ExVar.var(recordVar), field)))));
    }
    return List.of(
        ExPipeline.pipeline(
            "extra_headers",
            ExList.list(entries.toArray(ExExpr[]::new)),
            ExCall.call(
                "Enum",
                "reject",
                ExAnonymousFn.compactFn(
                    ExClause.inlineClause(
                        List.of(ExVarPattern.var("x")),
                        ExCall.call("Kernel", "is_nil", ExVar.var("x")))))));
  }

  private static List<ExExpr> buildRequestBodyExprs(
      Model model,
      HttpBindingIndex httpIndex,
      List<HttpBinding> reqPayload,
      List<HttpBinding> docMembers,
      String method,
      SymbolProvider sp,
      String recordVar,
      String eventStreamModule) {
    List<ExExpr> exprs = new ArrayList<>();
    boolean hasBody =
        !docMembers.isEmpty()
            && !method.equals("GET")
            && !method.equals("DELETE")
            && !method.equals("HEAD");

    if (!reqPayload.isEmpty()
        && !method.equals("GET")
        && !method.equals("DELETE")
        && !method.equals("HEAD")) {
      HttpBinding payload = reqPayload.get(0);
      MemberShape member = payload.getMember();
      String field = fieldName(sp, member);
      if (isStreamingBlob(model, member)) {
        exprs.add(ExMatch.match(ExVarPattern.var("body"), ExString.string("")));
        return exprs;
      }
      if (BeamEventStreamIndex.of(model).isEventStreamMember(member)) {
        UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        exprs.add(
            ExMatch.match(
                ExVarPattern.var("body"),
                ExCall.call(
                    eventStreamModule,
                    "encode_" + helper,
                    ExStructAccess.structAccess(ExVar.var(recordVar), field))));
        return exprs;
      }
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof BlobShape || target instanceof StringShape) {
        exprs.add(
            ExMatch.match(
                ExVarPattern.var("body"),
                ExOp.op(
                    "||",
                    ExStructAccess.structAccess(ExVar.var(recordVar), field),
                    ExString.string(""))));
        return exprs;
      }
    }

    if (hasBody) {
      List<MemberShape> docMemberShapes = docMembers.stream().map(HttpBinding::getMember).toList();
      List<ExMapEntry> entries =
          ElixirJsonCodecIr.bodyMapEntries(
              model,
              httpIndex,
              sp,
              "Types",
              docMemberShapes,
              recordVar,
              eventStreamModule);
      exprs.add(ElixirJsonCodecIr.rejectNilMapPipeline("body_map", entries));
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("body"),
              ExCall.call("Jason", "encode!", ExVar.var("body_map"))));
    } else {
      exprs.add(ExMatch.match(ExVarPattern.var("body"), ExString.string("")));
    }
    return exprs;
  }

  private static List<ExExpr> buildRequestCompressionExprs(OperationShape op) {
    if (!supportsGzipCompression(op)) {
      return List.of();
    }
    return List.of(
        ExMatch.match(ExVarPattern.var("headers1"), ExVar.var("headers")),
        ExMatch.match(
            ExTuplePattern.tuple(ExVarPattern.var("body"), ExVarPattern.var("headers")),
            ExCase.caseExpr(
                ExOp.op(
                    ">=",
                    ExCall.call(":erlang", "byte_size", ExVar.var("body")),
                    ExInteger.integer(10240)),
                ExCaseBranch.branch(
                    ExAtomPattern.atom("true"),
                    ExExprBlock.block(
                        ExMatch.match(
                            ExVarPattern.var("compressed"),
                            ExCall.call(":zlib", "gzip", ExVar.var("body"))),
                        ExTuple.tuple(
                            ExVar.var("compressed"),
                            ExCallLocal.callLocal(
                                "headers_set",
                                ExString.string("Content-Encoding"),
                                ExString.string("gzip"),
                                ExVar.var("headers1"))))),
                ExCaseBranch.branch(
                    ExAtomPattern.atom("false"),
                    ExTuple.tuple(ExVar.var("body"), ExVar.var("headers1"))))));
  }

  private static ExStruct buildHttpRequestStruct(
      String runtimeMod, String method, boolean streamingRequestPayload, boolean hasHostLabels) {
    List<ExMapEntry> fields = new ArrayList<>();
    fields.add(ExMapEntry.entry(ExAtom.atom("method"), ExString.string(method)));
    fields.add(ExMapEntry.entry(ExAtom.atom("path"), ExVar.var("path")));
    fields.add(ExMapEntry.entry(ExAtom.atom("query"), ExVar.var("query")));
    fields.add(ExMapEntry.entry(ExAtom.atom("headers"), ExVar.var("headers")));
    fields.add(ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body")));
    if (streamingRequestPayload) {
      fields.add(ExMapEntry.entry(ExAtom.atom("stream"), ExVar.var("stream")));
    }
    if (hasHostLabels) {
      fields.add(ExMapEntry.entry(ExAtom.atom("host"), ExVar.var("host")));
    }
    return ExStruct.struct(runtimeMod + ".HttpRequest", fields);
  }

  private static ExExpr buildPathExpression(
      String uriTemplate, List<HttpBinding> labels, SymbolProvider sp, String inputVar) {
    if (labels.isEmpty()) {
      return ExString.string(uriTemplate);
    }
    Map<String, HttpBinding> byLocation = new HashMap<>();
    for (HttpBinding lb : labels) {
      byLocation.put(lb.getLocationName(), lb);
    }
    ExExpr expr = null;
    int pos = 0;
    while (pos < uriTemplate.length()) {
      int start = uriTemplate.indexOf('{', pos);
      if (start < 0) {
        expr = appendPathSegment(expr, ExString.string(uriTemplate.substring(pos)));
        break;
      }
      if (start > pos) {
        expr = appendPathSegment(expr, ExString.string(uriTemplate.substring(pos, start)));
      }
      int end = uriTemplate.indexOf('}', start);
      String labelName = uriTemplate.substring(start + 1, end);
      HttpBinding lb = byLocation.get(labelName);
      if (lb != null) {
        String field = fieldName(sp, lb.getMember());
        expr =
            appendPathSegment(
                expr,
                ExCallLocal.callLocal(
                    "uri_encode",
                    ExStructAccess.structAccess(ExVar.var(inputVar), field)));
      } else {
        expr = appendPathSegment(expr, ExString.string("{" + labelName + "}"));
      }
      pos = end + 1;
    }
    return expr;
  }

  private static ExExpr appendPathSegment(ExExpr current, ExExpr segment) {
    return current == null ? segment : ExOp.op("<>", current, segment);
  }

  private static ExPipeCase headerValuePipeCase(String headersVar, String locationName) {
    return ExPipeCase.pipeCase(
        ExCall.call(
            "List",
            "keyfind",
            ExVar.var(headersVar),
            ExString.string(locationName),
            ExInteger.integer(0)),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExVarPattern.var("_"), ExVarPattern.var("v")),
            ExVar.var("v")),
        ExCaseBranch.branch(ExNilPattern.nil(), ExAtom.atom("nil")));
  }

  private static ExExpr decodeDocumentFieldExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member) {
    ExExpr raw =
        ExCall.call(
            "Map",
            "get",
            ExVar.var("decoded"),
            ExString.string(jsonKey(member)));
    return ElixirJsonCodecIr.decodeJsonExpr(model, sp, httpIndex, member, raw);
  }

  private static ExTuple buildErrorTuple(
      String typesMod,
      String modName,
      Model model,
      StructureShape errShape,
      SymbolProvider sp) {
    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : errShape.members()) {
      if (member.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = fieldName(sp, member);
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              ExCall.call(
                  "Map",
                  "get",
                  ExVar.var("decoded"),
                  ExString.string(member.getMemberName()))));
    }
    return ExTuple.tuple(
        ExAtom.atom("error"),
        ExCallLocal.callLocal(
            "struct!",
            ExVar.var(typesMod + "." + modName),
            ExMap.map(fields.toArray(ExMapEntry[]::new))));
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    Symbol sym = sp.toSymbol(member);
    return sym.getProperty("fieldName", String.class)
        .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
  }

  private static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }

  private static boolean supportsGzipCompression(OperationShape op) {
    return BeamRequestCompressionIndex.forOperation(op)
        .map(
            trait ->
                trait.getEncodings().stream()
                    .anyMatch(encoding -> encoding.equalsIgnoreCase("gzip")))
        .orElse(false);
  }

  private static boolean hasStreamingRequestPayload(
      Model model, List<HttpBinding> reqPayload, String method) {
    if (reqPayload.isEmpty()) {
      return false;
    }
    if (method != null
        && (method.equals("GET") || method.equals("DELETE") || method.equals("HEAD"))) {
      return false;
    }
    return isStreamingBlob(model, reqPayload.get(0).getMember());
  }

  private static boolean isStreamingBlob(Model model, MemberShape member) {
    Shape target = model.expectShape(member.getTarget());
    return target instanceof BlobShape && target.hasTrait(StreamingTrait.class);
  }

  private static String resolvedRequestContentType(Model model, OperationShape op) {
    return BeamHttpBindings.from(model)
        .requestContentType(op, "application/json")
        .orElse("application/json");
  }

  private static String resolvedResponseContentType(Model model, OperationShape op) {
    return BeamHttpBindings.from(model)
        .responseContentType(op, "application/json")
        .orElse("application/json");
  }

  private static boolean responsePayloadRequiresContentTypeCheck(
      Model model, List<HttpBinding> respPayload) {
    if (respPayload.isEmpty()) {
      return false;
    }
    Shape target = model.expectShape(respPayload.get(0).getMember().getTarget());
    return target.hasTrait(MediaTypeTrait.class);
  }

  @SafeVarargs
  private static <T> List<T> concat(List<T>... lists) {
    List<T> out = new ArrayList<>();
    for (List<T> list : lists) {
      out.addAll(list);
    }
    return out;
  }
}
