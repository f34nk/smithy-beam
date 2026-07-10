package io.smithy.beam.erlang;

import io.beam.ir.erlang.AndGuard;
import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.BinarySegmentExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.ExpressionGuard;
import io.beam.ir.erlang.Fun;
import io.beam.ir.erlang.FunClause;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.Guard;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.IntegerExpr;
import io.beam.ir.erlang.IntegerPattern;
import io.beam.ir.erlang.IsTypeGuard;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.NotEqualGuard;
import io.beam.ir.erlang.Pattern;
import io.beam.ir.erlang.RecordExpr;
import io.beam.ir.erlang.RecordField;
import io.beam.ir.erlang.RecordFieldAccessExpr;
import io.beam.ir.erlang.RecordPattern;
import io.beam.ir.erlang.RecordPatternField;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamRequestCompressionIndex;
import java.util.ArrayList;
import java.util.List;
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
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class ErlangRestJsonOperationIr {
  private ErlangRestJsonOperationIr() {}

  static Function buildEncodeRequest(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      boolean encodeWithConfig,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = recordName(sp.toSymbol(input));
    String inputType = sp.toSymbol(input).getName();
    HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
    String method = httpTrait.getMethod();
    String uriTemplate = httpTrait.getUri().toString();

    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
    List<HttpBinding> queryParams =
        httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
    List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> prefixHeaders =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> reqPayload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

    List<HttpBinding> patternBindings =
        concat(labels, queries, queryParams, headers, prefixHeaders, docMembers, reqPayload);
    String inputArgs = encodeWithConfig ? "client_config(), " + inputType : inputType;
    String specText = "encode_" + opName + "_request(" + inputArgs + ") -> #http_request{}";
    List<Pattern> patterns =
        encodeWithConfig
            ? List.of(
                VariablePattern.of("Config"),
                recordBindingHead("Input", inputRecord, patternBindings))
            : List.of(recordBindingHead("Input", inputRecord, patternBindings));

    List<Expression> body = new ArrayList<>();
    body.addAll(buildIdempotencyTokenExprs(input, inputRecord));
    body.add(MatchExpr.bindValue("Path", buildPathExpression(uriTemplate, labels)));
    body.add(MatchExpr.bindValue("Query", buildQueryListExpr(model, queries)));
    body.addAll(buildQueryParamsExprs(queryParams));
    body.addAll(buildRequestHeadersExprs(model, op, headers, prefixHeaders, sp));
    body.addAll(
        buildRequestBodyExprs(
            model, httpIndex, reqPayload, docMembers, method, sp, eventStreamModule));
    ErlangHttpChecksumIr.requestChecksumHeadersExpr(model, op, sp, "Headers").ifPresent(body::add);
    body.addAll(buildRequestCompressionExprs(op));

    boolean streamingRequestPayload = hasStreamingRequestPayload(model, reqPayload, method);
    if (streamingRequestPayload) {
      HttpBinding payload = reqPayload.get(0);
      String fieldName = BeamNameUtils.toSnakeCase(payload.getMember().getMemberName());
      body.add(
          MatchExpr.bindValue(
              "Stream",
              CaseExpr.of(
                  Variable.of(toBindingVar(fieldName)),
                  List.of(
                      Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                      Clause.of(VariablePattern.of("Value"), Variable.of("Value"))))));
    }

    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    boolean hasHostLabels =
        !hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class);
    if (hasHostLabels) {
      body.add(
          MatchExpr.bindValue(
              "Host",
              LocalCallExpr.of(
                  "build_host", List.of(Variable.of("Input"), Variable.of("Config")))));
    }

    String requestHeaders =
        BeamHttpChecksumIndex.of(model).requestChecksums(op).isEmpty()
            ? "Headers"
            : "HeadersWithChecksum";
    body.add(
        buildHttpRequestRecord(method, requestHeaders, streamingRequestPayload, hasHostLabels));

    return Function.of(
        "encode_" + opName + "_request",
        List.of(FunctionClause.of(patterns, BlockExpr.commaSeparated(body, false))),
        Spec.of(specText),
        Edoc.of("Encode HTTP request for " + op.getId() + "."));
  }

  static Function buildDecodeRequest(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = recordName(sp.toSymbol(input));
    String inputType = sp.toSymbol(input).getName();

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

    String specText =
        labels.isEmpty()
            ? "decode_" + opName + "_request(#http_request{}) -> " + inputType
            : "decode_" + opName + "_request(#http_request{}, map()) -> " + inputType;

    List<Pattern> patterns = new ArrayList<>();
    patterns.add(httpRequestPattern(streamingRequestPayload));
    if (!labels.isEmpty()) {
      patterns.add(VariablePattern.of("LabelMap"));
    }

    List<Expression> body = new ArrayList<>();
    if (!docMembers.isEmpty()) {
      body.addAll(ErlangJsonCodecSupport.decodedBodyPrelude());
    }
    body.add(
        buildInputRecord(
            inputRecord,
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

    return Function.of(
        "decode_" + opName + "_request",
        List.of(FunctionClause.of(patterns, BlockExpr.commaSeparated(body, false))),
        Spec.of(specText),
        Edoc.of("Decode HTTP request for " + op.getId() + "."));
  }

  static Function buildDecodeResponse(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      BeamErlangLayout layout) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = recordName(sp.toSymbol(output));
    String outputType = sp.toSymbol(output).getName();
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
    boolean eventStreamResponsePayload =
        !respPayload.isEmpty()
            && BeamEventStreamIndex.of(model).isEventStreamMember(respPayload.get(0).getMember());
    String eventStreamModule = layout.eventStreamModuleName();

    String specText =
        "decode_"
            + opName
            + "_response(#http_response{}) -> {'ok', "
            + outputType
            + "} | {'error', term()}";

    List<Guard> successGuards = new ArrayList<>();
    List<RecordPatternField> successFields = new ArrayList<>();
    successFields.add(RecordPatternField.of("status", VariablePattern.of("HttpStatus")));
    successFields.add(RecordPatternField.of("headers", VariablePattern.of("Headers")));
    successFields.add(RecordPatternField.of("body", VariablePattern.of("Body")));
    if (streamingResponsePayload) {
      successFields.add(RecordPatternField.of("stream", VariablePattern.of("Stream")));
    }
    if (!respCode.isEmpty()) {
      successGuards.add(
          ExpressionGuard.of(InfixExpr.of(Variable.of("HttpStatus"), ">=", IntegerExpr.of(200))));
      successGuards.add(
          ExpressionGuard.of(InfixExpr.of(Variable.of("HttpStatus"), "<", IntegerExpr.of(300))));
    }

    RecordPattern successPattern;
    if (!respCode.isEmpty()) {
      successPattern = RecordPattern.of("http_response", successFields);
    } else {
      successFields.set(0, RecordPatternField.of("status", IntegerPattern.of(successCode)));
      successPattern = RecordPattern.of("http_response", successFields);
    }

    List<Expression> successBody = new ArrayList<>();
    boolean needsContentTypeCheck = responsePayloadRequiresContentTypeCheck(model, respPayload);
    if (needsContentTypeCheck) {
      String expectedContentType = resolvedResponseContentType(model, op);
      List<Clause> contentTypeClauses = new ArrayList<>();
      contentTypeClauses.add(
          Clause.of(
              AtomPattern.of("false"),
              TupleExpr.of(
                  List.of(
                      AtomExpr.of("error"),
                      TupleExpr.of(
                          List.of(
                              AtomExpr.of("invalid_content_type"),
                              RemoteCallExpr.of(
                                  "proplists",
                                  "get_value",
                                  List.of(
                                      BinaryExpr.of("Content-Type"),
                                      Variable.of("Headers"),
                                      AtomExpr.of("undefined")))))))));
      contentTypeClauses.add(
          Clause.of(
              AtomPattern.of("true"),
              buildDecodeResponseSuccessBody(
                  model,
                  op,
                  httpIndex,
                  sp,
                  outputRecord,
                  respHeaders,
                  respPrefixHeaders,
                  respDoc,
                  respPayload,
                  respCode,
                  streamingResponsePayload,
                  eventStreamResponsePayload,
                  eventStreamModule,
                  needsContentTypeCheck)));
      successBody.add(
          CaseExpr.of(
              LocalCallExpr.of(
                  "content_type_matches",
                  List.of(Variable.of("Headers"), BinaryExpr.of(expectedContentType))),
              contentTypeClauses));
    } else {
      successBody.add(
          buildDecodeResponseSuccessBody(
              model,
              op,
              httpIndex,
              sp,
              outputRecord,
              respHeaders,
              respPrefixHeaders,
              respDoc,
              respPayload,
              respCode,
              streamingResponsePayload,
              eventStreamResponsePayload,
              eventStreamModule,
              needsContentTypeCheck));
    }

    FunctionClause successClause =
        successGuards.isEmpty()
            ? FunctionClause.of(
                List.of(successPattern), BlockExpr.commaSeparated(successBody, false))
            : FunctionClause.of(
                List.of(successPattern),
                AndGuard.of(successGuards),
                BlockExpr.commaSeparated(successBody, false));

    FunctionClause errorClause =
        FunctionClause.of(
            List.of(httpResponseErrorPattern()),
            LocalCallExpr.of(
                "decode_" + opName + "_response_error",
                List.of(Variable.of("Status"), Variable.of("RespHeaders"), Variable.of("Body"))));

    return Function.of(
        "decode_" + opName + "_response",
        List.of(successClause, errorClause),
        Spec.of(specText),
        Edoc.of("Decode HTTP response for " + op.getId() + "."));
  }

  static Function buildErrorDispatch(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp) {
    String opName = sp.toSymbol(op).getName();
    List<ShapeId> errors = new ArrayList<>(op.getErrors());

    List<FunctionClause> clauses = new ArrayList<>();
    for (ShapeId errorId : errors) {
      StructureShape errShape = model.expectShape(errorId, StructureShape.class);
      int httpStatus =
          errShape.hasTrait(HttpErrorTrait.class)
              ? errShape.expectTrait(HttpErrorTrait.class).getCode()
              : -1;
      if (httpStatus <= 0) {
        continue;
      }
      String recName = recordName(sp.toSymbol(errShape));
      clauses.add(
          FunctionClause.of(
              List.of(
                  IntegerPattern.of(httpStatus),
                  VariablePattern.of("_Hdrs"),
                  VariablePattern.of("Body")),
              BlockExpr.commaSeparated(
                  List.of(
                      MatchExpr.bindValue(
                          "Decoded",
                          LocalCallExpr.of("decode_json_body", List.of(Variable.of("Body")))),
                      TupleExpr.of(
                          List.of(
                              AtomExpr.of("error"),
                              buildErrorRecord(recName, model, errShape, sp)))),
                  false)));
    }

    boolean hasTypeDiscriminated =
        errors.stream()
            .anyMatch(
                e -> !model.expectShape(e, StructureShape.class).hasTrait(HttpErrorTrait.class));

    if (hasTypeDiscriminated) {
      List<Clause> typeClauses = new ArrayList<>();
      for (ShapeId errorId : errors) {
        StructureShape errShape = model.expectShape(errorId, StructureShape.class);
        if (errShape.hasTrait(HttpErrorTrait.class)) {
          continue;
        }
        String recName = recordName(sp.toSymbol(errShape));
        typeClauses.add(
            Clause.of(
                BinaryPattern.of(errorId.getName()),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"), buildErrorRecord(recName, model, errShape, sp)))));
      }
      typeClauses.add(
          Clause.of(
              WildcardPattern.of(),
              TupleExpr.of(
                  List.of(
                      AtomExpr.of("error"),
                      TupleExpr.of(
                          List.of(
                              AtomExpr.of("unknown_error"),
                              Variable.of("Status"),
                              Variable.of("Body")))))));
      clauses.add(
          FunctionClause.of(
              List.of(
                  VariablePattern.of("Status"),
                  VariablePattern.of("_Hdrs"),
                  VariablePattern.of("Body")),
              ExpressionGuard.of(InfixExpr.of(Variable.of("Status"), ">=", IntegerExpr.of(400))),
              BlockExpr.commaSeparated(
                  List.of(
                      MatchExpr.bindValue(
                          "Decoded",
                          LocalCallExpr.of("decode_json_body", List.of(Variable.of("Body")))),
                      MatchExpr.bindValue(
                          "ErrorType",
                          RemoteCallExpr.of(
                              "maps",
                              "get",
                              List.of(
                                  BinaryExpr.of("__type"),
                                  Variable.of("Decoded"),
                                  AtomExpr.of("undefined")))),
                      CaseExpr.of(Variable.of("ErrorType"), typeClauses)),
                  false)));
    } else {
      clauses.add(
          FunctionClause.of(
              List.of(
                  VariablePattern.of("Status"),
                  VariablePattern.of("_Hdrs"),
                  VariablePattern.of("Body")),
              TupleExpr.of(
                  List.of(
                      AtomExpr.of("error"),
                      TupleExpr.of(
                          List.of(
                              AtomExpr.of("unknown_error"),
                              Variable.of("Status"),
                              Variable.of("Body")))))));
    }

    return Function.of(
        "decode_" + opName + "_response_error",
        clauses,
        null,
        Edoc.of("Error dispatch for " + op.getId() + "."));
  }

  static Function buildErrorDispatch(Model model, OperationShape op, SymbolProvider sp) {
    return buildErrorDispatch(model, null, op, HttpBindingIndex.of(model), sp);
  }

  static Function buildEncodeResponse(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = recordName(sp.toSymbol(output));
    String outputType = sp.toSymbol(output).getName();

    String specText = "encode_" + opName + "_response(" + outputType + ") -> #http_response{}";

    return Function.of(
        "encode_" + opName + "_response",
        List.of(
            FunctionClause.of(
                List.of(encodeResponsePattern(model, op, httpIndex, sp, output, outputRecord)),
                BlockExpr.commaSeparated(
                    buildEncodeResponseBodyExprs(model, op, httpIndex, sp), false))),
        Spec.of(specText),
        Edoc.of("Encode HTTP response for " + op.getId() + "."));
  }

  static List<Expression> buildEncodeResponseBodyExprs(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    int successCode = httpIndex.getResponseCode(op);
    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    String responseContentType = resolvedResponseContentType(model, op);

    List<Expression> body = new ArrayList<>();
    body.addAll(buildResponseHeadersExprs(respHeaders, responseContentType));
    for (HttpBinding ph : respPrefixHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
      body.add(
          MatchExpr.bindValue(
              "Headers",
              InfixExpr.of(
                  Variable.of("Headers"),
                  "++",
                  LocalCallExpr.of(
                      "prefix_headers_to_list",
                      List.of(
                          BinaryExpr.of(ph.getLocationName()),
                          Variable.of(toBindingVar(fieldName)))))));
    }

    boolean streamingResponsePayload =
        !respPayload.isEmpty() && isStreamingBlob(model, respPayload.get(0).getMember());
    if (!respPayload.isEmpty()) {
      HttpBinding pb = respPayload.get(0);
      String fieldName = BeamNameUtils.toSnakeCase(pb.getMember().getMemberName());
      String bindingVar = toBindingVar(fieldName);
      if (streamingResponsePayload) {
        body.add(
            MatchExpr.bindValue(
                "Stream",
                CaseExpr.of(
                    Variable.of(bindingVar),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(VariablePattern.of("Value"), Variable.of("Value"))))));
        body.add(MatchExpr.bindValue("Body", BinaryExpr.of("")));
      } else {
        body.add(MatchExpr.bindValue("Body", Variable.of(bindingVar)));
      }
    } else if (!respDoc.isEmpty()) {
      List<MemberShape> docMembers = respDoc.stream().map(HttpBinding::getMember).toList();
      List<MapEntry> entries =
          ErlangJsonCodecSupport.bodyMapEntries(
              model, httpIndex, sp, docMembers, HttpBinding.Location.DOCUMENT, null);
      body.add(
          MatchExpr.bindValue(
              "BodyMap",
              RemoteCallExpr.of(
                  "maps",
                  "filter",
                  List.of(
                      Fun.of(
                          List.of(
                              FunClause.of(
                                  List.of(WildcardPattern.of(), VariablePattern.of("V")),
                                  InfixExpr.of(
                                      Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                      MapExpr.of(entries)))));
      body.add(
          MatchExpr.bindValue(
              "Body", RemoteCallExpr.of("jsone", "encode", List.of(Variable.of("BodyMap")))));
    } else {
      body.add(MatchExpr.bindValue("Body", BinaryExpr.of("")));
    }

    List<RecordField> recordFields = new ArrayList<>();
    recordFields.add(RecordField.of("status", IntegerExpr.of(successCode)));
    recordFields.add(RecordField.of("headers", Variable.of("Headers")));
    recordFields.add(RecordField.of("body", Variable.of("Body")));
    if (streamingResponsePayload) {
      recordFields.add(RecordField.of("stream", Variable.of("Stream")));
    }
    body.add(RecordExpr.of("http_response", recordFields));
    return body;
  }

  private static List<Expression> buildResponseHeadersExprs(
      List<HttpBinding> respHeaders, String responseContentType) {
    List<Expression> exprs = new ArrayList<>();
    if (respHeaders.isEmpty()) {
      exprs.add(
          MatchExpr.bindValue(
              "Headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              BinaryExpr.of("Content-Type"),
                              BinaryExpr.of(responseContentType)))))));
      return exprs;
    }

    List<FunClause> headerClauses = new ArrayList<>();
    for (HttpBinding hb : respHeaders) {
      headerClauses.add(
          FunClause.of(
              VariablePattern.of("V"),
              NotEqualGuard.of(Variable.of("V"), AtomExpr.of("undefined")),
              TupleExpr.of(
                  List.of(
                      AtomExpr.of("true"),
                      TupleExpr.of(
                          List.of(
                              BinaryExpr.of(hb.getLocationName()),
                              LocalCallExpr.of("to_binary", List.of(Variable.of("V")))))))));
    }
    headerClauses.add(FunClause.of(WildcardPattern.of(), AtomExpr.of("false")));
    List<Expression> headerArgs =
        respHeaders.stream()
            .map(
                hb ->
                    (Expression)
                        Variable.of(
                            toBindingVar(
                                BeamNameUtils.toSnakeCase(hb.getMember().getMemberName()))))
            .toList();
    exprs.add(
        MatchExpr.bindValue(
            "ExtraHeaders",
            RemoteCallExpr.of(
                "lists", "filtermap", List.of(Fun.of(headerClauses), ListExpr.of(headerArgs)))));
    exprs.add(
        MatchExpr.bindValue(
            "Headers",
            ListExpr.of(
                List.of(
                    TupleExpr.of(
                        List.of(
                            BinaryExpr.of("Content-Type"), BinaryExpr.of(responseContentType)))),
                Variable.of("ExtraHeaders"))));
    return exprs;
  }

  private static RecordPattern encodeResponsePattern(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      StructureShape output,
      String outputRecord) {
    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

    List<RecordPatternField> fields = new ArrayList<>();
    for (HttpBinding b : concat(respHeaders, respPrefixHeaders, respDoc, respPayload)) {
      String field = BeamNameUtils.toSnakeCase(b.getMember().getMemberName());
      fields.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    return RecordPattern.of(outputRecord, fields);
  }

  static Function buildErrorResponseEncoder(Model model, ShapeId errorId, SymbolProvider sp) {
    StructureShape errShape = model.expectShape(errorId, StructureShape.class);
    String recName = recordName(sp.toSymbol(errShape));
    int status =
        errShape.hasTrait(HttpErrorTrait.class)
            ? errShape.expectTrait(HttpErrorTrait.class).getCode()
            : 500;

    List<RecordPatternField> fields = new ArrayList<>();
    for (MemberShape m : errShape.members()) {
      if (m.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = BeamNameUtils.toSnakeCase(m.getMemberName());
      fields.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }

    List<MapEntry> bodyEntries = new ArrayList<>();
    bodyEntries.add(MapEntry.of(BinaryExpr.of("__type"), BinaryExpr.of(errorId.getName())));
    for (MemberShape m : errShape.members()) {
      if (m.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = BeamNameUtils.toSnakeCase(m.getMemberName());
      bodyEntries.add(
          MapEntry.of(BinaryExpr.of(m.getMemberName()), Variable.of(toBindingVar(field))));
    }

    Expression body =
        BlockExpr.commaSeparated(
            List.of(
                MatchExpr.bindValue("BodyMap", MapExpr.of(bodyEntries)),
                MatchExpr.bindValue(
                    "Body", RemoteCallExpr.of("jsone", "encode", List.of(Variable.of("BodyMap")))),
                RecordExpr.of(
                    "http_response",
                    List.of(
                        RecordField.of("status", IntegerExpr.of(status)),
                        RecordField.of(
                            "headers",
                            ListExpr.of(
                                List.of(
                                    TupleExpr.of(
                                        List.of(
                                            BinaryExpr.of("Content-Type"),
                                            BinaryExpr.of("application/json")))))),
                        RecordField.of("body", Variable.of("Body"))))),
            false);

    return Function.of(
        "encode_" + recName + "_response",
        List.of(FunctionClause.of(List.of(RecordPattern.of(recName, fields)), body)),
        Spec.of("encode_" + recName + "_response(#" + recName + "{}) -> #http_response{}"),
        Edoc.of("Encode HTTP error response for " + errorId + "."));
  }

  static RecordPattern memberBindingHead(
      String alias, String recordName, StructureShape structure, SymbolProvider sp) {
    List<RecordPatternField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      fields.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    return RecordPattern.bind(alias, recordName, fields);
  }

  static RecordPattern outputBindingHead(
      String recordName, StructureShape structure, SymbolProvider sp) {
    List<RecordPatternField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      fields.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    return RecordPattern.of(recordName, fields);
  }

  static RecordExpr buildDocumentRecordFromDecoded(
      String recordName,
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<MemberShape> members,
      String eventStreamModule) {
    List<RecordField> fields =
        ErlangJsonCodecSupport.recordFieldsFromDecoded(
            model, httpIndex, sp, members, HttpBinding.Location.DOCUMENT, eventStreamModule);
    return RecordExpr.of(recordName, fields);
  }

  static List<Expression> buildDocumentBodyEncodeExprs(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<MemberShape> members,
      String eventStreamModule) {
    if (ErlangJsonCodecSupport.isEventStreamPayload(members, model)) {
      MemberShape member = members.get(0);
      UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
      String helper = ErlangEventStreamEmitter.helperName(sp, union);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      return List.of(
          MatchExpr.bindValue(
              "Body",
              LocalCallExpr.of(
                  "iolist_to_binary",
                  List.of(
                      RemoteCallExpr.of(
                          eventStreamModule,
                          "encode_" + helper,
                          List.of(Variable.of(toBindingVar(fieldName))))))));
    }
    List<MapEntry> entries =
        ErlangJsonCodecSupport.bodyMapEntries(
            model, httpIndex, sp, members, HttpBinding.Location.DOCUMENT, eventStreamModule);
    return List.of(
        MatchExpr.bindValue(
            "BodyMap",
            RemoteCallExpr.of(
                "maps",
                "filter",
                List.of(
                    Fun.of(
                        List.of(
                            FunClause.of(
                                List.of(WildcardPattern.of(), VariablePattern.of("V")),
                                InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                    MapExpr.of(entries)))),
        MatchExpr.bindValue(
            "Body", RemoteCallExpr.of("jsone", "encode", List.of(Variable.of("BodyMap")))));
  }

  private static Expression buildDecodeResponseSuccessBody(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String outputRecord,
      List<HttpBinding> respHeaders,
      List<HttpBinding> respPrefixHeaders,
      List<HttpBinding> respDoc,
      List<HttpBinding> respPayload,
      List<HttpBinding> respCode,
      boolean streamingResponsePayload,
      boolean eventStreamResponsePayload,
      String eventStreamModule,
      boolean needsContentTypeCheck) {
    List<Expression> body = new ArrayList<>();
    if (!respDoc.isEmpty()
        || (!respPayload.isEmpty() && !needsContentTypeCheck && !eventStreamResponsePayload)) {
      body.addAll(ErlangJsonCodecSupport.decodedBodyPrelude());
    }
    for (HttpBinding hb : respHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
      String bindingVar = toBindingVar(fieldName);
      body.add(
          MatchExpr.bindValue(
              bindingVar,
              RemoteCallExpr.of(
                  "proplists",
                  "get_value",
                  List.of(
                      BinaryExpr.of(hb.getLocationName()),
                      Variable.of("Headers"),
                      AtomExpr.of("undefined")))));
    }

    List<RecordField> recordFields = new ArrayList<>();
    for (HttpBinding hb : respHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
      recordFields.add(RecordField.of(fieldName, Variable.of(toBindingVar(fieldName))));
    }
    for (HttpBinding ph : respPrefixHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
      recordFields.add(
          RecordField.of(
              fieldName,
              LocalCallExpr.of(
                  "prefix_headers_from_list",
                  List.of(Variable.of("Headers"), BinaryExpr.of(ph.getLocationName())))));
    }
    for (HttpBinding db : respDoc) {
      String fieldName = BeamNameUtils.toSnakeCase(db.getMember().getMemberName());
      String wireKey = jsonKey(db.getMember());
      Expression raw =
          ErlangCodecHelperIr.mapsGetDefault(
              BinaryExpr.of(wireKey), Variable.of("Decoded"), AtomExpr.of("undefined"));
      recordFields.add(
          RecordField.of(fieldName, decodeJsonExpr(model, sp, httpIndex, db.getMember(), raw)));
    }
    for (HttpBinding pb : respPayload) {
      String fieldName = BeamNameUtils.toSnakeCase(pb.getMember().getMemberName());
      if (isStreamingBlob(model, pb.getMember())) {
        recordFields.add(RecordField.of(fieldName, Variable.of("Stream")));
      } else if (BeamEventStreamIndex.of(model).isEventStreamMember(pb.getMember())) {
        UnionShape union = model.expectShape(pb.getMember().getTarget(), UnionShape.class);
        String helper = ErlangEventStreamEmitter.helperName(sp, union);
        recordFields.add(
            RecordField.of(
                fieldName,
                RemoteCallExpr.of(
                    eventStreamModule, "decode_" + helper, List.of(Variable.of("Body")))));
      } else {
        recordFields.add(RecordField.of(fieldName, Variable.of("Body")));
      }
    }
    for (HttpBinding rcb : respCode) {
      String fieldName = BeamNameUtils.toSnakeCase(rcb.getMember().getMemberName());
      recordFields.add(RecordField.of(fieldName, Variable.of("HttpStatus")));
    }

    Expression success =
        TupleExpr.of(List.of(AtomExpr.of("ok"), RecordExpr.of(outputRecord, recordFields)));
    body.add(ErlangHttpChecksumIr.responseChecksumGuardExpr(model, op, success));
    return body.size() == 1 ? body.get(0) : BlockExpr.commaSeparated(body, false);
  }

  private static RecordExpr buildInputRecord(
      String inputRecord,
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
    List<RecordField> fields = new ArrayList<>();
    for (HttpBinding lb : labels) {
      String fieldName = BeamNameUtils.toSnakeCase(lb.getMember().getMemberName());
      fields.add(
          RecordField.of(
              fieldName,
              LocalCallExpr.of(
                  "uri_decode",
                  List.of(
                      RemoteCallExpr.of(
                          "maps",
                          "get",
                          List.of(
                              BinaryExpr.of(lb.getMember().getMemberName()),
                              Variable.of("LabelMap"),
                              AtomExpr.of("undefined")))))));
    }
    for (HttpBinding qb : queries) {
      String fieldName = BeamNameUtils.toSnakeCase(qb.getMember().getMemberName());
      fields.add(
          RecordField.of(
              fieldName,
              LocalCallExpr.of(
                  "decode_query_param",
                  List.of(
                      RemoteCallExpr.of(
                          "maps",
                          "get",
                          List.of(
                              BinaryExpr.of(qb.getLocationName()),
                              Variable.of("Query"),
                              AtomExpr.of("undefined")))))));
    }
    for (HttpBinding qp : queryParams) {
      String fieldName = BeamNameUtils.toSnakeCase(qp.getMember().getMemberName());
      fields.add(
          RecordField.of(
              fieldName,
              RemoteCallExpr.of(
                  "maps",
                  "from_list",
                  List.of(RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Query")))))));
    }
    for (HttpBinding hb : headers) {
      String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
      fields.add(
          RecordField.of(
              fieldName,
              RemoteCallExpr.of(
                  "proplists",
                  "get_value",
                  List.of(
                      BinaryExpr.of(hb.getLocationName()),
                      Variable.of("Headers"),
                      AtomExpr.of("undefined")))));
    }
    for (HttpBinding ph : prefixHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
      fields.add(
          RecordField.of(
              fieldName,
              LocalCallExpr.of(
                  "prefix_headers_from_list",
                  List.of(Variable.of("Headers"), BinaryExpr.of(ph.getLocationName())))));
    }
    for (HttpBinding db : docMembers) {
      String fieldName = BeamNameUtils.toSnakeCase(db.getMember().getMemberName());
      String wireKey = jsonKey(db.getMember());
      Expression raw =
          ErlangCodecHelperIr.mapsGetDefault(
              BinaryExpr.of(wireKey), Variable.of("Decoded"), AtomExpr.of("undefined"));
      fields.add(
          RecordField.of(fieldName, decodeJsonExpr(model, sp, httpIndex, db.getMember(), raw)));
    }
    for (HttpBinding pb : reqPayload) {
      String fieldName = BeamNameUtils.toSnakeCase(pb.getMember().getMemberName());
      if (isStreamingBlob(model, pb.getMember())) {
        fields.add(RecordField.of(fieldName, Variable.of("Stream")));
      } else if (BeamEventStreamIndex.of(model).isEventStreamMember(pb.getMember())) {
        UnionShape union = model.expectShape(pb.getMember().getTarget(), UnionShape.class);
        String helper = ErlangEventStreamEmitter.helperName(sp, union);
        fields.add(
            RecordField.of(
                fieldName,
                RemoteCallExpr.of(
                    eventStreamModule, "decode_" + helper, List.of(Variable.of("Body")))));
      } else {
        fields.add(RecordField.of(fieldName, Variable.of("Body")));
      }
    }
    return RecordExpr.of(inputRecord, fields);
  }

  static RecordExpr buildErrorRecord(
      String recName, Model model, StructureShape errShape, SymbolProvider sp) {
    List<RecordField> fields = new ArrayList<>();
    for (MemberShape member : errShape.members()) {
      if (member.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      fields.add(
          RecordField.of(
              field,
              RemoteCallExpr.of(
                  "maps",
                  "get",
                  List.of(
                      BinaryExpr.of(member.getMemberName()),
                      Variable.of("Decoded"),
                      AtomExpr.of("undefined")))));
    }
    return RecordExpr.of(recName, fields);
  }

  private static List<Expression> buildIdempotencyTokenExprs(
      StructureShape input, String inputRecord) {
    List<MemberShape> idempotencyMembers =
        input.members().stream().filter(m -> m.hasTrait(IdempotencyTokenTrait.class)).toList();
    if (idempotencyMembers.isEmpty()) {
      return List.of();
    }
    List<Expression> exprs = new ArrayList<>();
    String currentInput = "Input";
    int step = 1;
    for (MemberShape member : idempotencyMembers) {
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      String nextInput = "Input" + step;
      exprs.add(
          MatchExpr.bindValue(
              nextInput,
              CaseExpr.of(
                  RecordFieldAccessExpr.of(Variable.of(currentInput), inputRecord, field),
                  List.of(
                      Clause.of(
                          AtomPattern.of("undefined"),
                          RecordExpr.update(
                              Variable.of(currentInput),
                              inputRecord,
                              List.of(
                                  RecordField.of(
                                      field, LocalCallExpr.of("generate_uuid", List.of()))))),
                      Clause.of(WildcardPattern.of(), Variable.of(currentInput))))));
      currentInput = nextInput;
      step++;
    }
    for (MemberShape member : idempotencyMembers) {
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      exprs.add(
          MatchExpr.bindValue(
              toBindingVar(field),
              RecordFieldAccessExpr.of(Variable.of(currentInput), inputRecord, field)));
    }
    return exprs;
  }

  private static Expression buildPathExpression(String uriTemplate, List<HttpBinding> labels) {
    if (labels.isEmpty()) {
      return BinaryExpr.of(uriTemplate);
    }
    List<BinarySegmentExpr> segments = new ArrayList<>();
    int pos = 0;
    while (pos < uriTemplate.length()) {
      int start = uriTemplate.indexOf('{', pos);
      if (start < 0) {
        segments.add(BinarySegmentExpr.literal(uriTemplate.substring(pos)));
        break;
      }
      if (start > pos) {
        segments.add(BinarySegmentExpr.literal(uriTemplate.substring(pos, start)));
      }
      int end = uriTemplate.indexOf('}', start);
      String labelName = uriTemplate.substring(start + 1, end);
      String fieldName = BeamNameUtils.toSnakeCase(labelName);
      segments.add(
          BinarySegmentExpr.of(
              LocalCallExpr.of(
                  "uri_encode",
                  List.of(
                      LocalCallExpr.of(
                          "to_binary", List.of(Variable.of(toBindingVar(fieldName)))))),
              "binary"));
      pos = end + 1;
    }
    return BinaryExpr.of(segments);
  }

  private static Expression buildQueryListExpr(Model model, List<HttpBinding> queries) {
    if (queries.isEmpty()) {
      return ListExpr.of(List.of());
    }
    List<Expression> parts = new ArrayList<>();
    for (HttpBinding qb : queries) {
      String bindingVar = toBindingVar(BeamNameUtils.toSnakeCase(qb.getMember().getMemberName()));
      Shape target = model.expectShape(qb.getMember().getTarget());
      Fun fun = queryFiltermapFun(qb.getLocationName());
      Expression listArg =
          target instanceof ListShape
              ? CaseExpr.of(
                  Variable.of(bindingVar),
                  List.of(
                      Clause.of(AtomPattern.of("undefined"), ListExpr.of(List.of())),
                      Clause.of(VariablePattern.of("V"), Variable.of("V"))))
              : ListExpr.of(List.of(Variable.of(bindingVar)));
      parts.add(RemoteCallExpr.of("lists", "filtermap", List.of(fun, listArg)));
    }
    if (parts.size() == 1) {
      return parts.get(0);
    }
    Expression combined = parts.get(0);
    for (int i = 1; i < parts.size(); i++) {
      combined = InfixExpr.of(combined, "++", parts.get(i));
    }
    return combined;
  }

  private static Fun queryFiltermapFun(String paramName) {
    return Fun.of(
        List.of(
            FunClause.of(
                VariablePattern.of("V"),
                NotEqualGuard.of(Variable.of("V"), AtomExpr.of("undefined")),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("true"),
                        TupleExpr.of(
                            List.of(
                                BinaryExpr.of(paramName),
                                LocalCallExpr.of(
                                    "encode_query_value", List.of(Variable.of("V")))))))),
            FunClause.of(WildcardPattern.of(), AtomExpr.of("false"))));
  }

  private static List<Expression> buildQueryParamsExprs(List<HttpBinding> queryParams) {
    if (queryParams.isEmpty()) {
      return List.of();
    }
    List<Expression> exprs = new ArrayList<>();
    for (HttpBinding qp : queryParams) {
      String fieldName = BeamNameUtils.toSnakeCase(qp.getMember().getMemberName());
      String bindingVar = toBindingVar(fieldName);
      exprs.add(
          MatchExpr.bindValue(
              "QueryExtra",
              CaseExpr.of(
                  Variable.of(bindingVar),
                  List.of(
                      Clause.of(AtomPattern.of("undefined"), ListExpr.of(List.of())),
                      Clause.of(
                          VariablePattern.of("M"),
                          IsTypeGuard.of("map", Variable.of("M")),
                          ListComprehensionExpr.of(
                              TupleExpr.of(List.of(Variable.of("K"), Variable.of("V"))),
                              TuplePattern.of(
                                  List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                              RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("M")))))))));
      exprs.add(
          MatchExpr.bindValue(
              "Query", InfixExpr.of(Variable.of("Query"), "++", Variable.of("QueryExtra"))));
    }
    return exprs;
  }

  private static List<Expression> buildRequestHeadersExprs(
      Model model,
      OperationShape op,
      List<HttpBinding> headers,
      List<HttpBinding> prefixHeaders,
      SymbolProvider sp) {
    String requestContentType = resolvedRequestContentType(model, op);
    List<Expression> exprs = new ArrayList<>();
    if (headers.isEmpty()) {
      exprs.add(
          MatchExpr.bindValue(
              "Headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              BinaryExpr.of("Content-Type"),
                              BinaryExpr.of(requestContentType)))))));
    } else {
      List<FunClause> headerClauses = new ArrayList<>();
      for (HttpBinding hb : headers) {
        headerClauses.add(
            FunClause.of(
                VariablePattern.of("V"),
                NotEqualGuard.of(Variable.of("V"), AtomExpr.of("undefined")),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("true"),
                        TupleExpr.of(
                            List.of(
                                BinaryExpr.of(hb.getLocationName()),
                                LocalCallExpr.of("to_binary", List.of(Variable.of("V")))))))));
      }
      headerClauses.add(FunClause.of(WildcardPattern.of(), AtomExpr.of("false")));
      List<Expression> headerArgs =
          headers.stream()
              .map(
                  hb ->
                      (Expression)
                          Variable.of(
                              toBindingVar(
                                  BeamNameUtils.toSnakeCase(hb.getMember().getMemberName()))))
              .toList();
      exprs.add(
          MatchExpr.bindValue(
              "Headers0",
              RemoteCallExpr.of(
                  "lists", "filtermap", List.of(Fun.of(headerClauses), ListExpr.of(headerArgs)))));
      exprs.add(
          MatchExpr.bindValue(
              "Headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              BinaryExpr.of("Content-Type"), BinaryExpr.of(requestContentType)))),
                  Variable.of("Headers0"))));
    }
    for (HttpBinding ph : prefixHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
      exprs.add(
          MatchExpr.bindValue(
              "Headers",
              InfixExpr.of(
                  Variable.of("Headers"),
                  "++",
                  LocalCallExpr.of(
                      "prefix_headers_to_list",
                      List.of(
                          BinaryExpr.of(ph.getLocationName()),
                          Variable.of(toBindingVar(fieldName)))))));
    }
    return exprs;
  }

  private static List<Expression> buildRequestBodyExprs(
      Model model,
      HttpBindingIndex httpIndex,
      List<HttpBinding> reqPayload,
      List<HttpBinding> docMembers,
      String method,
      SymbolProvider sp,
      String eventStreamModule) {
    List<Expression> exprs = new ArrayList<>();
    if (!reqPayload.isEmpty()
        && !method.equals("GET")
        && !method.equals("DELETE")
        && !method.equals("HEAD")) {
      HttpBinding payload = reqPayload.get(0);
      MemberShape member = payload.getMember();
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      String bindingVar = toBindingVar(fieldName);
      if (isStreamingBlob(model, member)) {
        exprs.add(MatchExpr.bindValue("Body", BinaryExpr.of("")));
        return exprs;
      }
      if (BeamEventStreamIndex.of(model).isEventStreamMember(member)) {
        UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
        String helper = ErlangEventStreamEmitter.helperName(sp, union);
        exprs.add(
            MatchExpr.bindValue(
                "Body",
                LocalCallExpr.of(
                    "iolist_to_binary",
                    List.of(
                        RemoteCallExpr.of(
                            eventStreamModule,
                            "encode_" + helper,
                            List.of(Variable.of(bindingVar)))))));
        return exprs;
      }
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof BlobShape || target instanceof StringShape) {
        exprs.add(
            MatchExpr.bindValue(
                "Body",
                CaseExpr.of(
                    Variable.of(bindingVar),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")),
                        Clause.of(VariablePattern.of("Value"), Variable.of("Value"))))));
        return exprs;
      }
    }

    if (docMembers.isEmpty()
        || method.equals("GET")
        || method.equals("DELETE")
        || method.equals("HEAD")) {
      exprs.add(MatchExpr.bindValue("Body", BinaryExpr.of("")));
    } else {
      List<MemberShape> docMemberShapes = docMembers.stream().map(HttpBinding::getMember).toList();
      List<MapEntry> entries =
          ErlangJsonCodecSupport.bodyMapEntries(
              model,
              httpIndex,
              sp,
              docMemberShapes,
              HttpBinding.Location.DOCUMENT,
              eventStreamModule);
      exprs.add(
          MatchExpr.bindValue(
              "BodyMap",
              RemoteCallExpr.of(
                  "maps",
                  "filter",
                  List.of(
                      Fun.of(
                          List.of(
                              FunClause.of(
                                  List.of(WildcardPattern.of(), VariablePattern.of("V")),
                                  InfixExpr.of(
                                      Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                      MapExpr.of(entries)))));
      exprs.add(
          MatchExpr.bindValue(
              "Body", RemoteCallExpr.of("jsone", "encode", List.of(Variable.of("BodyMap")))));
    }
    return exprs;
  }

  private static RecordExpr buildHttpRequestRecord(
      String method, String headersVar, boolean streamingRequestPayload, boolean hasHostLabels) {
    List<RecordField> fields = new ArrayList<>();
    fields.add(RecordField.of("method", BinaryExpr.of(method)));
    fields.add(RecordField.of("path", Variable.of("Path")));
    fields.add(
        RecordField.of(
            "query", RemoteCallExpr.of("maps", "from_list", List.of(Variable.of("Query")))));
    fields.add(RecordField.of("headers", Variable.of(headersVar)));
    fields.add(RecordField.of("body", Variable.of("Body")));
    if (streamingRequestPayload) {
      fields.add(RecordField.of("stream", Variable.of("Stream")));
    }
    if (hasHostLabels) {
      fields.add(RecordField.of("host", Variable.of("Host")));
    }
    return RecordExpr.of("http_request", fields);
  }

  static CaseExpr decodeBodyJsonExpr() {
    return ErlangJsonCodecSupport.decodedBodyExpr();
  }

  static Expression decodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression raw) {
    return ErlangJsonCodecSupport.decodeJsonExpr(model, sp, httpIndex, member, raw);
  }

  static Expression encodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String bindingVar) {
    return ErlangJsonCodecSupport.encodeJsonExpr(model, sp, httpIndex, member, bindingVar);
  }

  private static RecordPattern recordBindingHead(
      String alias, String recordName, List<HttpBinding> bindings) {
    List<RecordPatternField> fields = new ArrayList<>();
    for (HttpBinding binding : bindings) {
      String field = BeamNameUtils.toSnakeCase(binding.getMember().getMemberName());
      fields.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    return RecordPattern.bind(alias, recordName, fields);
  }

  private static RecordPattern httpRequestPattern(boolean streaming) {
    List<RecordPatternField> fields = new ArrayList<>();
    fields.add(RecordPatternField.of("query", VariablePattern.of("Query")));
    fields.add(RecordPatternField.of("headers", VariablePattern.of("Headers")));
    fields.add(RecordPatternField.of("body", VariablePattern.of("Body")));
    if (streaming) {
      fields.add(RecordPatternField.of("stream", VariablePattern.of("Stream")));
    }
    return RecordPattern.of("http_request", fields);
  }

  private static RecordPattern httpResponseErrorPattern() {
    return RecordPattern.of(
        "http_response",
        List.of(
            RecordPatternField.of("status", VariablePattern.of("Status")),
            RecordPatternField.of("headers", VariablePattern.of("RespHeaders")),
            RecordPatternField.of("body", VariablePattern.of("Body"))));
  }

  private static List<Expression> buildRequestCompressionExprs(OperationShape op) {
    if (!supportsGzipCompression(op)) {
      return List.of();
    }
    return List.of(
        MatchExpr.bindValue("Headers1", Variable.of("Headers")),
        MatchExpr.bindValue(
            "Body",
            CaseExpr.of(
                InfixExpr.of(
                    LocalCallExpr.of("byte_size", List.of(Variable.of("Body"))),
                    ">=",
                    IntegerExpr.of(10240)),
                List.of(
                    Clause.of(
                        AtomPattern.of("true"),
                        BlockExpr.commaSeparated(
                            List.of(
                                MatchExpr.bindValue(
                                    "Compressed",
                                    RemoteCallExpr.of(
                                        "zlib", "gzip", List.of(Variable.of("Body")))),
                                TupleExpr.of(
                                    List.of(
                                        Variable.of("Compressed"),
                                        LocalCallExpr.of(
                                            "headers_set",
                                            List.of(
                                                BinaryExpr.of("Content-Encoding"),
                                                BinaryExpr.of("gzip"),
                                                Variable.of("Headers1")))))),
                            false)),
                    Clause.of(
                        AtomPattern.of("false"),
                        TupleExpr.of(List.of(Variable.of("Body"), Variable.of("Headers1"))))))));
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

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  private static String toBindingVar(String snakeField) {
    return ErlangJsonCodecSupport.toBindingVar(snakeField);
  }

  private static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }

  private static String timestampEncodeHelper(
      HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
    TimestampFormatTrait.Format fmt =
        httpIndex.determineTimestampFormat(member, location, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "encode_timestamp_epoch_seconds"
        : "encode_timestamp_date_time";
  }

  private static String timestampDecodeHelper(
      HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
    TimestampFormatTrait.Format fmt =
        httpIndex.determineTimestampFormat(member, location, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "decode_timestamp_epoch_seconds"
        : "decode_timestamp_date_time";
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
