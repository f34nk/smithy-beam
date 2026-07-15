package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AndGuard;
import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AssignPattern;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.ComparisonGuard;
import io.beam.dsl.elixir.DotCallExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionDoc;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.Guard;
import io.beam.dsl.elixir.IfExpr;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.IntegerPattern;
import io.beam.dsl.elixir.IsTypeGuard;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.NilExpr;
import io.beam.dsl.elixir.NilPattern;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.PipeExpr;
import io.beam.dsl.elixir.PipeStep;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StringPattern;
import io.beam.dsl.elixir.StructExpr;
import io.beam.dsl.elixir.StructField;
import io.beam.dsl.elixir.StructPattern;
import io.beam.dsl.elixir.StructPatternField;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamRequestCompressionIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.ListShape;
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

final class ElixirRestJsonOperationDsl {
  private ElixirRestJsonOperationDsl() {}

  static List<Function> buildEncodeRequest(
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

    List<Pattern> patterns =
        encodeWithConfig
            ? List.of(VariablePattern.of("config"), VariablePattern.of("input"))
            : List.of(VariablePattern.of("input"));

    List<Expression> body = new ArrayList<>();
    body.addAll(buildIdempotencyTokenExprs(input, sp));
    body.add(MatchExpr.bind("path", buildPathExpression(uriTemplate, labels, sp, "input")));
    body.addAll(buildQueryExprs(model, queries, sp));
    body.addAll(buildQueryParamsExprs(queryParams, sp));
    body.addAll(buildRequestHeadersExprs(model, op, headers, prefixHeaders, sp, "input"));
    body.addAll(
        buildRequestBodyExprs(
            model, httpIndex, reqPayload, docMembers, method, sp, "input", eventStreamModule));
    ElixirHttpChecksumDsl.requestChecksumHeadersExpr(model, op, sp, "headers").ifPresent(body::add);
    body.addAll(buildRequestCompressionExprs(op));

    if (streamingRequestPayload) {
      HttpBinding payload = reqPayload.get(0);
      String field = fieldName(sp, payload.getMember());
      body.add(MatchExpr.bind("stream", DotCallExpr.of(Variable.of("input"), field, List.of())));
      body.add(MatchExpr.bind("body", StringExpr.of("")));
    }

    if (hasHostLabels) {
      body.add(
          MatchExpr.bind(
              "host",
              LocalCallExpr.of(
                  "build_host", List.of(Variable.of("input"), Variable.of("config")))));
    }

    body.add(buildHttpRequestStruct(runtimeMod, method, streamingRequestPayload, hasHostLabels));

    Spec spec =
        encodeWithConfig
            ? Spec.of(
                "encode_" + opName + "_request(map(), " + inputType + ") :: " + httpRequestType)
            : Spec.of("encode_" + opName + "_request(" + inputType + ") :: " + httpRequestType);

    return List.of(
        Function.of(
            "encode_" + opName + "_request",
            false,
            List.of(FunctionHead.of(patterns)),
            block(body),
            spec,
            null,
            false));
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

    List<StructPatternField> requestFields = new ArrayList<>();
    requestFields.add(field("query", VariablePattern.of("query")));
    requestFields.add(field("headers", VariablePattern.of("headers")));
    requestFields.add(field("body", VariablePattern.of("body")));
    if (streamingRequestPayload) {
      requestFields.add(field("stream", VariablePattern.of("stream")));
    }

    List<Pattern> patterns = new ArrayList<>();
    patterns.add(StructPattern.of(runtimeMod + ".HttpRequest", requestFields));
    if (!labels.isEmpty()) {
      patterns.add(VariablePattern.of("label_map"));
    }

    List<Expression> body = new ArrayList<>();
    if (!docMembers.isEmpty()) {
      body.addAll(ElixirJsonCodecDsl.decodedBodyPrelude());
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
    Spec spec =
        labels.isEmpty()
            ? Spec.of(
                "decode_" + opName + "_request(%" + runtimeMod + ".HttpRequest{}) :: " + returnType)
            : Spec.of(
                "decode_"
                    + opName
                    + "_request(%"
                    + runtimeMod
                    + ".HttpRequest{}, map()) :: "
                    + returnType);

    return List.of(
        def(
            "decode_" + opName + "_request",
            patterns,
            block(body),
            spec,
            FunctionDoc.of("Decode HTTP request for " + op.getId() + "."),
            false));
  }

  static List<Function> buildDecodeResponse(
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

    List<StructPatternField> successFields = new ArrayList<>();
    if (!respCode.isEmpty()) {
      successFields.add(field("status", VariablePattern.of("http_status")));
    } else {
      successFields.add(field("status", IntegerPattern.of(successCode)));
    }
    successFields.add(field("headers", VariablePattern.of("headers")));
    successFields.add(field("body", VariablePattern.of("body")));
    if (streamingResponsePayload) {
      successFields.add(field("stream", VariablePattern.of("stream")));
    }

    List<Guard> successGuards = new ArrayList<>();
    if (!respCode.isEmpty()) {
      successGuards.add(ComparisonGuard.of(Variable.of("http_status"), ">=", IntegerExpr.of(200)));
      successGuards.add(ComparisonGuard.of(Variable.of("http_status"), "<", IntegerExpr.of(300)));
    }

    List<Expression> successBody = new ArrayList<>();
    boolean needsContentTypeCheck = responsePayloadRequiresContentTypeCheck(model, respPayload);
    if (needsContentTypeCheck) {
      String expectedContentType = resolvedResponseContentType(model, op);
      successBody.add(
          MatchExpr.bind(
              AtomPattern.of("ok"),
              LocalCallExpr.of(
                  "content_type_matches",
                  List.of(Variable.of("headers"), StringExpr.of(expectedContentType))),
              block(
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
                      streamingResponsePayload))));
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

    List<Function> functions = new ArrayList<>();
    FunctionHead successHead =
        successGuards.isEmpty()
            ? FunctionHead.of(
                List.of(StructPattern.of(runtimeMod + ".HttpResponse", successFields)))
            : FunctionHead.of(
                List.of(StructPattern.of(runtimeMod + ".HttpResponse", successFields)),
                AndGuard.of(successGuards));
    functions.add(
        Function.of(
            "decode_" + opName + "_response",
            false,
            List.of(successHead),
            block(successBody),
            null,
            null,
            false));

    List<StructPatternField> errorFields =
        List.of(
            field("status", VariablePattern.of("status")),
            field("headers", VariablePattern.of("headers")),
            field("body", VariablePattern.of("body")));
    functions.add(
        Function.of(
            "decode_" + opName + "_response",
            false,
            List.of(
                FunctionHead.of(
                    List.of(StructPattern.of(runtimeMod + ".HttpResponse", errorFields)))),
            LocalCallExpr.of(
                "decode_" + opName + "_response_error",
                List.of(Variable.of("status"), Variable.of("headers"), Variable.of("body"))),
            null,
            null,
            true));
    return functions;
  }

  static List<Function> buildErrorDispatch(
      Model model, OperationShape op, SymbolProvider sp, String typesMod) {
    String opName = sp.toSymbol(op).getName();
    List<ShapeId> errors = new ArrayList<>(op.getErrors());

    List<Function> functions = new ArrayList<>();

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
      functions.add(
          defp(
              "decode_" + opName + "_response_error",
              List.of(
                  IntegerPattern.of(httpStatus),
                  VariablePattern.of("_headers"),
                  VariablePattern.of("body")),
              block(
                  List.of(
                      MatchExpr.bind(
                          "decoded",
                          LocalCallExpr.of("decode_json_body", List.of(Variable.of("body")))),
                      buildErrorTuple(typesMod, modName, model, errShape, sp))),
              false));
    }

    boolean hasTypeDiscriminated =
        errors.stream()
            .anyMatch(
                e -> !model.expectShape(e, StructureShape.class).hasTrait(HttpErrorTrait.class));

    if (hasTypeDiscriminated) {
      List<Clause> typeBranches = new ArrayList<>();
      for (ShapeId errorId : errors) {
        StructureShape errShape = model.expectShape(errorId, StructureShape.class);
        if (errShape.hasTrait(HttpErrorTrait.class)) {
          continue;
        }
        String modName = sp.toSymbol(errShape).getName();
        typeBranches.add(
            Clause.of(
                StringPattern.of(errorId.getName()),
                buildErrorTuple(typesMod, modName, model, errShape, sp)));
      }
      Expression unknownError =
          TupleExpr.of(
              List.of(
                  AtomExpr.of("error"),
                  TupleExpr.of(
                      List.of(
                          AtomExpr.of("unknown_error"),
                          Variable.of("status"),
                          Variable.of("body")))));
      typeBranches.add(Clause.of(WildcardPattern.of(), unknownError));

      functions.add(
          defp(
              "decode_" + opName + "_response_error",
              List.of(
                  VariablePattern.of("status"),
                  VariablePattern.of("_headers"),
                  VariablePattern.of("body")),
              ComparisonGuard.of(Variable.of("status"), ">=", IntegerExpr.of(400)),
              block(
                  List.of(
                      MatchExpr.bind(
                          "decoded",
                          LocalCallExpr.of("decode_json_body", List.of(Variable.of("body")))),
                      MatchExpr.bind(
                          "error_type",
                          RemoteCallExpr.of(
                              "Map",
                              "get",
                              List.of(Variable.of("decoded"), StringExpr.of("__type")))),
                      CaseExpr.of(Variable.of("error_type"), typeBranches))),
              false));
    } else {
      functions.add(
          defp(
              "decode_" + opName + "_response_error",
              List.of(
                  VariablePattern.of("status"),
                  VariablePattern.of("_headers"),
                  VariablePattern.of("body")),
              TupleExpr.of(
                  List.of(
                      AtomExpr.of("error"),
                      TupleExpr.of(
                          List.of(
                              AtomExpr.of("unknown_error"),
                              Variable.of("status"),
                              Variable.of("body"))))),
              true));
    }

    return functions;
  }

  static List<Function> buildEncodeResponse(
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

    List<StructPatternField> patternFields = new ArrayList<>();
    for (HttpBinding binding : concat(respHeaders, respPrefixHeaders, respDoc, respPayload)) {
      String field = fieldName(sp, binding.getMember());
      patternFields.add(field(field, VariablePattern.of("_" + field)));
    }

    return List.of(
        def(
            "encode_" + opName + "_response",
            List.of(
                AssignPattern.of(
                    "output", StructPattern.of("Types." + outputStruct, patternFields))),
            block(buildEncodeResponseBodyExprs(model, op, httpIndex, sp, "output")),
            Spec.of("encode_" + opName + "_response(" + outputType + ") :: map()"),
            FunctionDoc.of("Encode response for " + op.getId() + "."),
            false));
  }

  static List<Function> buildErrorResponseEncoder(
      Model model, ShapeId errorId, SymbolProvider sp, String typesMod) {
    StructureShape errShape = model.expectShape(errorId, StructureShape.class);
    String modName = sp.toSymbol(errShape).getName();
    int status =
        errShape.hasTrait(HttpErrorTrait.class)
            ? errShape.expectTrait(HttpErrorTrait.class).getCode()
            : 500;

    List<StructPatternField> patternFields = new ArrayList<>();
    List<MapEntry> bodyEntries = new ArrayList<>();
    bodyEntries.add(MapEntry.stringKey("__type", StringExpr.of(errorId.getName())));
    for (MemberShape member : errShape.members()) {
      if (member.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = fieldName(sp, member);
      patternFields.add(field(field, VariablePattern.of("_" + field)));
      bodyEntries.add(
          MapEntry.stringKey(
              member.getMemberName(), DotCallExpr.of(Variable.of("error"), field, List.of())));
    }

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bind(
            "body_map", ElixirJsonCodecDsl.rejectNilMapPipeline("body_map", bodyEntries)));
    body.add(
        MatchExpr.bind(
            "body", RemoteCallExpr.of("Jason", "encode!", List.of(Variable.of("body_map")))));
    body.add(
        MatchExpr.bind(
            "headers",
            ListExpr.of(
                List.of(
                    TupleExpr.of(
                        List.of(
                            StringExpr.of("Content-Type"), StringExpr.of("application/json")))))));
    body.add(
        MapExpr.of(
            List.of(
                MapEntry.atomKey("status", IntegerExpr.of(status)),
                MapEntry.atomKey("headers", Variable.of("headers")),
                MapEntry.atomKey("body", Variable.of("body")))));

    String errorType = typesMod + "." + modName + ".t()";
    return List.of(
        def(
            "encode_" + modName + "_response",
            List.of(AssignPattern.of("error", StructPattern.of("Types." + modName, patternFields))),
            block(body),
            Spec.of("encode_" + modName + "_response(" + errorType + ") :: map()"),
            FunctionDoc.of("Encode HTTP error response for " + errorId + "."),
            false));
  }

  private static List<Expression> buildDecodeResponseSuccessBody(
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
    List<Expression> body = new ArrayList<>();
    if (!respDoc.isEmpty()) {
      body.addAll(ElixirJsonCodecDsl.decodedBodyPrelude());
    }

    for (HttpBinding hb : respHeaders) {
      String field = fieldName(sp, hb.getMember());
      body.add(
          MatchExpr.bind(
              field,
              LocalCallExpr.of(
                  "header_value",
                  List.of(Variable.of("headers"), StringExpr.of(hb.getLocationName())))));
    }

    List<StructField> structFields = new ArrayList<>();
    for (HttpBinding hb : respHeaders) {
      String field = fieldName(sp, hb.getMember());
      structFields.add(StructField.of(field, Variable.of(field)));
    }
    for (HttpBinding ph : respPrefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      structFields.add(
          StructField.of(
              field,
              LocalCallExpr.of(
                  "prefix_headers_from_list",
                  List.of(Variable.of("headers"), StringExpr.of(ph.getLocationName())))));
    }
    for (HttpBinding db : respDoc) {
      String field = fieldName(sp, db.getMember());
      structFields.add(
          StructField.of(field, decodeDocumentFieldExpr(model, sp, httpIndex, db.getMember())));
    }
    for (HttpBinding pb : respPayload) {
      String field = fieldName(sp, pb.getMember());
      if (isStreamingBlob(model, pb.getMember())) {
        structFields.add(StructField.of(field, Variable.of("stream")));
      } else {
        structFields.add(StructField.of(field, Variable.of("body")));
      }
    }
    for (HttpBinding rcb : respCode) {
      String field = fieldName(sp, rcb.getMember());
      structFields.add(StructField.of(field, Variable.of("http_status")));
    }

    Expression success =
        TupleExpr.of(
            List.of(AtomExpr.of("ok"), StructExpr.of("Types." + outputStruct, structFields)));
    body.add(MatchExpr.bind("result", success));
    body.add(ElixirHttpChecksumDsl.responseChecksumGuardExpr(model, op, Variable.of("result")));
    return body;
  }

  private static StructExpr buildInputStruct(
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
    List<StructField> fields = new ArrayList<>();
    for (HttpBinding lb : labels) {
      String field = fieldName(sp, lb.getMember());
      fields.add(
          StructField.of(
              field,
              LocalCallExpr.of(
                  "uri_decode",
                  List.of(
                      RemoteCallExpr.of(
                          "Map",
                          "get",
                          List.of(
                              Variable.of("label_map"),
                              StringExpr.of(lb.getMember().getMemberName())))))));
    }
    for (HttpBinding qb : queries) {
      String field = fieldName(sp, qb.getMember());
      fields.add(
          StructField.of(
              field,
              LocalCallExpr.of(
                  "decode_query_param",
                  List.of(
                      RemoteCallExpr.of(
                          "Map",
                          "get",
                          List.of(Variable.of("query"), StringExpr.of(qb.getLocationName())))))));
    }
    for (HttpBinding qp : queryParams) {
      String field = fieldName(sp, qp.getMember());
      fields.add(StructField.of(field, Variable.of("query")));
    }
    for (HttpBinding hb : headers) {
      String field = fieldName(sp, hb.getMember());
      fields.add(
          StructField.of(
              field,
              LocalCallExpr.of(
                  "header_value",
                  List.of(Variable.of("headers"), StringExpr.of(hb.getLocationName())))));
    }
    for (HttpBinding ph : prefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      fields.add(
          StructField.of(
              field,
              LocalCallExpr.of(
                  "prefix_headers_from_list",
                  List.of(Variable.of("headers"), StringExpr.of(ph.getLocationName())))));
    }
    for (HttpBinding db : docMembers) {
      String field = fieldName(sp, db.getMember());
      fields.add(
          StructField.of(field, decodeDocumentFieldExpr(model, sp, httpIndex, db.getMember())));
    }
    for (HttpBinding pb : reqPayload) {
      String field = fieldName(sp, pb.getMember());
      if (isStreamingBlob(model, pb.getMember())) {
        fields.add(StructField.of(field, Variable.of("stream")));
      } else if (BeamEventStreamIndex.of(model).isEventStreamMember(pb.getMember())) {
        UnionShape union = model.expectShape(pb.getMember().getTarget(), UnionShape.class);
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        fields.add(
            StructField.of(
                field,
                RemoteCallExpr.of(
                    eventStreamModule, "decode_" + helper, List.of(Variable.of("body")))));
      } else {
        fields.add(StructField.of(field, Variable.of("body")));
      }
    }
    return StructExpr.of("Types." + inputStruct, fields);
  }

  private static List<Expression> buildEncodeResponseBodyExprs(
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

    List<Expression> body = new ArrayList<>();
    if (!respPayload.isEmpty()) {
      HttpBinding pb = respPayload.get(0);
      String field = fieldName(sp, pb.getMember());
      if (isStreamingBlob(model, pb.getMember())) {
        body.add(
            MatchExpr.bind("stream", DotCallExpr.of(Variable.of(recordVar), field, List.of())));
        body.add(MatchExpr.bind("body", StringExpr.of("")));
      } else {
        body.add(MatchExpr.bind("body", DotCallExpr.of(Variable.of(recordVar), field, List.of())));
      }
    } else if (!respDoc.isEmpty()) {
      List<MemberShape> docMemberShapes = respDoc.stream().map(HttpBinding::getMember).toList();
      List<MapEntry> entries =
          ElixirJsonCodecDsl.bodyMapEntries(
              model, httpIndex, sp, "Types", docMemberShapes, recordVar, "event_stream");
      body.add(
          MatchExpr.bind("body_map", ElixirJsonCodecDsl.rejectNilMapPipeline("body_map", entries)));
      body.add(
          MatchExpr.bind(
              "body", RemoteCallExpr.of("Jason", "encode!", List.of(Variable.of("body_map")))));
    } else {
      body.add(MatchExpr.bind("body", StringExpr.of("")));
    }

    if (!respHeaders.isEmpty()) {
      body.addAll(extraHeadersPipeline(sp, recordVar, respHeaders));
      body.add(
          MatchExpr.bind(
              "headers",
              RemoteCallExpr.of(
                  "Enum",
                  "concat",
                  List.of(
                      ListExpr.of(
                          List.of(
                              TupleExpr.of(
                                  List.of(
                                      StringExpr.of("Content-Type"),
                                      StringExpr.of(responseContentType))))),
                      Variable.of("extra_headers")))));
    } else {
      body.add(
          MatchExpr.bind(
              "headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              StringExpr.of("Content-Type"),
                              StringExpr.of(responseContentType)))))));
    }

    for (HttpBinding ph : respPrefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      body.add(
          MatchExpr.bind(
              "headers",
              InfixExpr.of(
                  Variable.of("headers"),
                  "++",
                  LocalCallExpr.of(
                      "prefix_headers_to_list",
                      List.of(
                          StringExpr.of(ph.getLocationName()),
                          DotCallExpr.of(Variable.of(recordVar), field, List.of()))))));
    }

    List<MapEntry> responseFields = new ArrayList<>();
    responseFields.add(MapEntry.atomKey("status", IntegerExpr.of(successCode)));
    responseFields.add(MapEntry.atomKey("headers", Variable.of("headers")));
    responseFields.add(MapEntry.atomKey("body", Variable.of("body")));
    if (!respPayload.isEmpty() && isStreamingBlob(model, respPayload.get(0).getMember())) {
      responseFields.add(MapEntry.atomKey("stream", Variable.of("stream")));
    }
    body.add(MapExpr.of(responseFields));
    return body;
  }

  private static List<Expression> buildIdempotencyTokenExprs(
      StructureShape input, SymbolProvider sp) {
    List<MemberShape> idempotencyMembers =
        input.members().stream().filter(m -> m.hasTrait(IdempotencyTokenTrait.class)).toList();
    if (idempotencyMembers.isEmpty()) {
      return List.of();
    }
    List<Expression> exprs = new ArrayList<>();
    for (MemberShape member : idempotencyMembers) {
      String field = fieldName(sp, member);
      exprs.add(
          MatchExpr.bind(
              "input",
              CaseExpr.of(
                  DotCallExpr.of(Variable.of("input"), field, List.of()),
                  List.of(
                      Clause.of(
                          NilPattern.of(),
                          MapExpr.of(
                              Variable.of("input"),
                              List.of(
                                  MapEntry.atomKey(
                                      field, LocalCallExpr.of("generate_uuid", List.of()))))),
                      Clause.of(WildcardPattern.of(), Variable.of("input"))))));
    }
    return exprs;
  }

  private static List<Expression> buildQueryExprs(
      Model model, List<HttpBinding> queries, SymbolProvider sp) {
    if (queries.isEmpty()) {
      return List.of(MatchExpr.bind("query", MapExpr.of(List.of())));
    }
    List<Expression> parts = new ArrayList<>();
    for (HttpBinding qb : queries) {
      parts.add(buildQueryBindingExpr(model, qb, sp));
    }
    Expression queryEntries = parts.get(0);
    for (int i = 1; i < parts.size(); i++) {
      queryEntries = RemoteCallExpr.of("Enum", "concat", List.of(queryEntries, parts.get(i)));
    }
    return List.of(MatchExpr.bind("query", RemoteCallExpr.of("Map", "new", List.of(queryEntries))));
  }

  private static Expression buildQueryBindingExpr(Model model, HttpBinding qb, SymbolProvider sp) {
    String field = fieldName(sp, qb.getMember());
    Expression binding = DotCallExpr.of(Variable.of("input"), field, List.of());
    String paramName = qb.getLocationName();
    Shape target = model.expectShape(qb.getMember().getTarget());
    Expression listArg =
        target instanceof ListShape
            ? CaseExpr.of(
                binding,
                List.of(
                    Clause.of(NilPattern.of(), ListExpr.of(List.of())),
                    Clause.of(VariablePattern.of("v"), Variable.of("v"))))
            : ListExpr.of(List.of(binding));
    return RemoteCallExpr.of(
        "Enum",
        "flat_map",
        List.of(
            listArg,
            AnonFun.of(
                List.of(
                    AnonFunClause.of(
                        List.of(VariablePattern.of("v")),
                        CaseExpr.of(
                            Variable.of("v"),
                            List.of(
                                Clause.of(NilPattern.of(), ListExpr.of(List.of())),
                                Clause.of(
                                    VariablePattern.of("item"),
                                    ListExpr.of(
                                        List.of(
                                            TupleExpr.of(
                                                List.of(
                                                    StringExpr.of(paramName),
                                                    LocalCallExpr.of(
                                                        "encode_query_value",
                                                        List.of(Variable.of("item")))))))))))))));
  }

  private static List<Expression> buildQueryParamsExprs(
      List<HttpBinding> queryParams, SymbolProvider sp) {
    if (queryParams.isEmpty()) {
      return List.of();
    }
    List<Expression> exprs = new ArrayList<>();
    for (HttpBinding qp : queryParams) {
      String field = fieldName(sp, qp.getMember());
      exprs.add(
          MatchExpr.bind(
              "query_extra",
              CaseExpr.of(
                  DotCallExpr.of(Variable.of("input"), field, List.of()),
                  List.of(
                      Clause.of(NilPattern.of(), ListExpr.of(List.of())),
                      Clause.of(
                          VariablePattern.of("m"),
                          IsTypeGuard.of("is_map", "m"),
                          RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("m"))))))));
      exprs.add(
          MatchExpr.bind(
              "query",
              PipeExpr.of(
                  Variable.of("query"),
                  List.of(
                      PipeStep.of(RemoteCallExpr.of("Map", "to_list", List.of()), List.of()),
                      PipeStep.of(
                          RemoteCallExpr.of("Enum", "concat", List.of(Variable.of("query_extra"))),
                          List.of()),
                      PipeStep.of(RemoteCallExpr.of("Map", "new", List.of()), List.of())))));
    }
    return exprs;
  }

  private static List<Expression> buildRequestHeadersExprs(
      Model model,
      OperationShape op,
      List<HttpBinding> headers,
      List<HttpBinding> prefixHeaders,
      SymbolProvider sp,
      String recordVar) {
    String requestContentType = resolvedRequestContentType(model, op);
    List<Expression> exprs = new ArrayList<>();
    if (headers.isEmpty()) {
      exprs.add(
          MatchExpr.bind(
              "headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              StringExpr.of("Content-Type"),
                              StringExpr.of(requestContentType)))))));
    } else {
      exprs.addAll(extraHeadersPipeline(sp, recordVar, headers));
      exprs.add(
          MatchExpr.bind(
              "headers",
              RemoteCallExpr.of(
                  "Enum",
                  "concat",
                  List.of(
                      ListExpr.of(
                          List.of(
                              TupleExpr.of(
                                  List.of(
                                      StringExpr.of("Content-Type"),
                                      StringExpr.of(requestContentType))))),
                      Variable.of("extra_headers")))));
    }
    for (HttpBinding ph : prefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      exprs.add(
          MatchExpr.bind(
              "headers",
              InfixExpr.of(
                  Variable.of("headers"),
                  "++",
                  LocalCallExpr.of(
                      "prefix_headers_to_list",
                      List.of(
                          StringExpr.of(ph.getLocationName()),
                          DotCallExpr.of(Variable.of(recordVar), field, List.of()))))));
    }
    return exprs;
  }

  private static List<Expression> extraHeadersPipeline(
      SymbolProvider sp, String recordVar, List<HttpBinding> headers) {
    List<Expression> entries = new ArrayList<>();
    for (HttpBinding hb : headers) {
      String field = fieldName(sp, hb.getMember());
      entries.add(
          IfExpr.of(
              InfixExpr.of(
                  DotCallExpr.of(Variable.of(recordVar), field, List.of()),
                  "!=",
                  AtomExpr.of("nil")),
              TupleExpr.of(
                  List.of(
                      StringExpr.of(hb.getLocationName()),
                      RemoteCallExpr.of(
                          "Kernel",
                          "to_string",
                          List.of(DotCallExpr.of(Variable.of(recordVar), field, List.of()))))),
              NilExpr.of(),
              false));
    }
    return List.of(
        MatchExpr.bind(
            "extra_headers",
            PipeExpr.of(
                ListExpr.of(entries),
                List.of(
                    PipeStep.of(
                        RemoteCallExpr.of(
                            "Enum",
                            "reject",
                            List.of(
                                AnonFun.of(
                                    List.of(
                                        AnonFunClause.of(
                                            List.of(VariablePattern.of("x")),
                                            LocalCallExpr.of(
                                                "is_nil", List.of(Variable.of("x")))))))),
                        List.of())))));
  }

  private static List<Expression> buildRequestBodyExprs(
      Model model,
      HttpBindingIndex httpIndex,
      List<HttpBinding> reqPayload,
      List<HttpBinding> docMembers,
      String method,
      SymbolProvider sp,
      String recordVar,
      String eventStreamModule) {
    List<Expression> exprs = new ArrayList<>();
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
        exprs.add(MatchExpr.bind("body", StringExpr.of("")));
        return exprs;
      }
      if (BeamEventStreamIndex.of(model).isEventStreamMember(member)) {
        UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        exprs.add(
            MatchExpr.bind(
                "body",
                RemoteCallExpr.of(
                    eventStreamModule,
                    "encode_" + helper,
                    List.of(DotCallExpr.of(Variable.of(recordVar), field, List.of())))));
        return exprs;
      }
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof BlobShape || target instanceof StringShape) {
        exprs.add(
            MatchExpr.bind(
                "body",
                InfixExpr.of(
                    DotCallExpr.of(Variable.of(recordVar), field, List.of()),
                    "||",
                    StringExpr.of(""))));
        return exprs;
      }
    }

    if (hasBody) {
      List<MemberShape> docMemberShapes = docMembers.stream().map(HttpBinding::getMember).toList();
      List<MapEntry> entries =
          ElixirJsonCodecDsl.bodyMapEntries(
              model, httpIndex, sp, "Types", docMemberShapes, recordVar, eventStreamModule);
      exprs.add(
          MatchExpr.bind("body_map", ElixirJsonCodecDsl.rejectNilMapPipeline("body_map", entries)));
      exprs.add(
          MatchExpr.bind(
              "body", RemoteCallExpr.of("Jason", "encode!", List.of(Variable.of("body_map")))));
    } else {
      exprs.add(MatchExpr.bind("body", StringExpr.of("")));
    }
    return exprs;
  }

  private static List<Expression> buildRequestCompressionExprs(OperationShape op) {
    if (!supportsGzipCompression(op)) {
      return List.of();
    }
    Expression gzipCase =
        CaseExpr.of(
            InfixExpr.of(
                RemoteCallExpr.of(":erlang", "byte_size", List.of(Variable.of("body"))),
                ">=",
                IntegerExpr.of(10240)),
            List.of(
                Clause.of(
                    AtomPattern.of("true"),
                    BlockExpr.of(
                        List.of(
                            MatchExpr.bind(
                                "compressed",
                                RemoteCallExpr.of(":zlib", "gzip", List.of(Variable.of("body")))),
                            TupleExpr.of(
                                List.of(
                                    Variable.of("compressed"),
                                    LocalCallExpr.of(
                                        "headers_set",
                                        List.of(
                                            StringExpr.of("Content-Encoding"),
                                            StringExpr.of("gzip"),
                                            Variable.of("headers1")))))))),
                Clause.of(
                    AtomPattern.of("false"),
                    TupleExpr.of(List.of(Variable.of("body"), Variable.of("headers1"))))));
    return List.of(
        MatchExpr.bind("headers1", Variable.of("headers")),
        MatchExpr.bind(
            TuplePattern.of(List.of(VariablePattern.of("body"), VariablePattern.of("headers"))),
            gzipCase));
  }

  private static StructExpr buildHttpRequestStruct(
      String runtimeMod, String method, boolean streamingRequestPayload, boolean hasHostLabels) {
    List<StructField> fields = new ArrayList<>();
    fields.add(StructField.of("method", StringExpr.of(method)));
    fields.add(StructField.of("path", Variable.of("path")));
    fields.add(StructField.of("query", Variable.of("query")));
    fields.add(StructField.of("headers", Variable.of("headers")));
    fields.add(StructField.of("body", Variable.of("body")));
    if (streamingRequestPayload) {
      fields.add(StructField.of("stream", Variable.of("stream")));
    }
    if (hasHostLabels) {
      fields.add(StructField.of("host", Variable.of("host")));
    }
    return StructExpr.of(runtimeMod + ".HttpRequest", fields);
  }

  private static Expression buildPathExpression(
      String uriTemplate, List<HttpBinding> labels, SymbolProvider sp, String inputVar) {
    if (labels.isEmpty()) {
      return StringExpr.of(uriTemplate);
    }
    Map<String, HttpBinding> byLocation = new HashMap<>();
    for (HttpBinding lb : labels) {
      byLocation.put(lb.getLocationName(), lb);
    }
    Expression expr = null;
    int pos = 0;
    while (pos < uriTemplate.length()) {
      int start = uriTemplate.indexOf('{', pos);
      if (start < 0) {
        expr = appendPathSegment(expr, StringExpr.of(uriTemplate.substring(pos)));
        break;
      }
      if (start > pos) {
        expr = appendPathSegment(expr, StringExpr.of(uriTemplate.substring(pos, start)));
      }
      int end = uriTemplate.indexOf('}', start);
      String labelName = uriTemplate.substring(start + 1, end);
      HttpBinding lb = byLocation.get(labelName);
      if (lb != null) {
        String field = fieldName(sp, lb.getMember());
        expr =
            appendPathSegment(
                expr,
                LocalCallExpr.of(
                    "uri_encode",
                    List.of(DotCallExpr.of(Variable.of(inputVar), field, List.of()))));
      } else {
        expr = appendPathSegment(expr, StringExpr.of("{" + labelName + "}"));
      }
      pos = end + 1;
    }
    return expr;
  }

  private static Expression appendPathSegment(Expression current, Expression segment) {
    return current == null ? segment : InfixExpr.of(current, "<>", segment);
  }

  private static Expression decodeDocumentFieldExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member) {
    return ElixirJsonCodecDsl.decodeJsonExpr(
        model,
        sp,
        httpIndex,
        member,
        RemoteCallExpr.of(
            "Map", "get", List.of(Variable.of("decoded"), StringExpr.of(jsonKey(member)))));
  }

  private static Expression buildErrorTuple(
      String typesMod, String modName, Model model, StructureShape errShape, SymbolProvider sp) {
    List<MapEntry> fields = new ArrayList<>();
    for (MemberShape member : errShape.members()) {
      if (member.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = fieldName(sp, member);
      fields.add(
          MapEntry.atomKey(
              field,
              RemoteCallExpr.of(
                  "Map",
                  "get",
                  List.of(Variable.of("decoded"), StringExpr.of(member.getMemberName())))));
    }
    return TupleExpr.of(
        List.of(
            AtomExpr.of("error"),
            LocalCallExpr.of(
                "struct!", List.of(Variable.of(typesMod + "." + modName), MapExpr.of(fields)))));
  }

  private static StructPatternField field(String name, Pattern pattern) {
    return StructPatternField.of(name, pattern);
  }

  private static Function def(
      String name,
      List<Pattern> params,
      Expression body,
      Spec spec,
      FunctionDoc doc,
      boolean oneLiner) {
    return Function.of(name, false, List.of(FunctionHead.of(params)), body, spec, doc, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return Function.of(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, Guard guard, Expression body, boolean oneLiner) {
    return Function.of(
        name, true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
  }

  private static Expression block(List<Expression> statements) {
    if (statements.isEmpty()) {
      return NilExpr.of();
    }
    if (statements.size() == 1) {
      return statements.get(0);
    }
    return BlockExpr.of(statements);
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
