package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.BinaryPattern;
import io.beam.dsl.erlang.BinarySegmentExpr;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Edoc;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.Fun;
import io.beam.dsl.erlang.FunClause;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.InfixExpr;
import io.beam.dsl.erlang.IntegerExpr;
import io.beam.dsl.erlang.IntegerPattern;
import io.beam.dsl.erlang.IsTypeGuard;
import io.beam.dsl.erlang.ListComprehensionExpr;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.ListPattern;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MapEntry;
import io.beam.dsl.erlang.MapExpr;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.Pattern;
import io.beam.dsl.erlang.RecordExpr;
import io.beam.dsl.erlang.RecordField;
import io.beam.dsl.erlang.RecordFieldAccessExpr;
import io.beam.dsl.erlang.RecordPattern;
import io.beam.dsl.erlang.RecordPatternField;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.Spec;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
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
import software.amazon.smithy.model.traits.MediaTypeTrait;

final class ErlangRestXmlOperationIr {
  private static final String DEFAULT_CONTENT_TYPE = "application/xml";

  private ErlangRestXmlOperationIr() {}

  static Function buildEncodeRequest(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      boolean encodeWithConfig) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(input));
    String inputType = sp.toSymbol(input).getName();

    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
    List<HttpBinding> queryParams =
        httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
    List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> prefixHeaders =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> payloadMembers =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
    List<HttpBinding> patternBindings =
        ErlangRestXmlSupport.concat(
            labels, queries, queryParams, headers, prefixHeaders, payloadMembers);

    String inputArgs = encodeWithConfig ? "client_config(), " + inputType : inputType;
    List<Pattern> patterns =
        encodeWithConfig
            ? List.of(
                VariablePattern.of("Config"),
                recordBindingHead("Input", inputRecord, patternBindings))
            : List.of(recordBindingHead("Input", inputRecord, patternBindings));

    return Function.of(
        "encode_" + opName + "_request",
        List.of(
            FunctionClause.of(
                patterns,
                BlockExpr.commaSeparated(
                    buildEncodeRequestBodyExprs(
                        model, service, op, httpIndex, sp, encodeWithConfig),
                    false))),
        Spec.of("encode_" + opName + "_request(" + inputArgs + ") -> #http_request{}"),
        Edoc.of("Encode REST-XML request for " + op.getId() + "."));
  }

  static Function buildDecodeRequest(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputType = sp.toSymbol(input).getName();
    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);

    String requestArgs = labels.isEmpty() ? "#http_request{}" : "map(), #http_request{}";
    List<Pattern> patterns =
        labels.isEmpty()
            ? List.of(httpRequestPattern())
            : List.of(VariablePattern.of("Labels"), httpRequestPattern());

    return Function.of(
        "decode_" + opName + "_request",
        List.of(
            FunctionClause.of(
                patterns,
                BlockExpr.commaSeparated(
                    buildDecodeRequestBodyExprs(model, op, httpIndex, sp), false))),
        Spec.of(
            "decode_"
                + opName
                + "_request("
                + requestArgs
                + ") -> {'ok', "
                + inputType
                + "} | {'error', term()}"),
        Edoc.of("Decode REST-XML request for " + op.getId() + "."));
  }

  static List<Function> buildDecodeResponse(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = sp.toSymbol(output).getName();
    int successCode = httpIndex.getResponseCode(op);

    List<Pattern> successPatterns =
        List.of(
            RecordPattern.of(
                "http_response",
                List.of(
                    RecordPatternField.of("status", IntegerPattern.of(successCode)),
                    RecordPatternField.of("headers", VariablePattern.of("Headers")),
                    RecordPatternField.of("body", VariablePattern.of("Body")))));

    List<Pattern> fallbackPatterns =
        List.of(
            RecordPattern.of(
                "http_response",
                List.of(
                    RecordPatternField.of("status", VariablePattern.of("Status")),
                    RecordPatternField.of("body", VariablePattern.of("Body")))));

    List<FunctionClause> clauses = new ArrayList<>();
    clauses.add(
        FunctionClause.of(
            successPatterns,
            BlockExpr.commaSeparated(
                buildDecodeResponseSuccessBodyExprs(model, op, httpIndex, sp), false)));
    clauses.add(FunctionClause.of(fallbackPatterns, buildDecodeResponseFallbackExprs(op, sp)));

    List<Function> functions = new ArrayList<>();
    functions.add(
        Function.of(
            "decode_" + opName + "_response",
            clauses,
            Spec.of(
                "decode_"
                    + opName
                    + "_response(#http_response{}) -> {'ok', "
                    + outputType
                    + "} | {'error', term()}"),
            Edoc.of("Decode REST-XML response for " + op.getId() + ".")));

    if (!op.getErrors().isEmpty()) {
      functions.add(
          Function.of(
              "decode_" + opName + "_response_error",
              ErlangRestXmlSupport.buildResponseErrorDispatchClauses(model, op, sp),
              Spec.of(
                  "decode_" + opName + "_response_error(integer(), term()) -> {'error', term()}"),
              Edoc.of("Error dispatch for " + op.getId() + ".")));
    }
    return functions;
  }

  static Function buildEncodeResponse(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(output));
    String outputType = sp.toSymbol(output).getName();

    return Function.of(
        "encode_" + opName + "_response",
        List.of(
            FunctionClause.of(
                List.of(encodeResponsePattern(model, op, httpIndex, sp, output, outputRecord)),
                BlockExpr.commaSeparated(
                    buildEncodeResponseBodyExprs(model, op, httpIndex, sp), false))),
        Spec.of("encode_" + opName + "_response(" + outputType + ") -> #http_response{}"),
        Edoc.of("Encode REST-XML response for " + op.getId() + "."));
  }

  static Function buildErrorResponseEncoder(Model model, ShapeId errorId, SymbolProvider sp) {
    StructureShape errShape = model.expectShape(errorId, StructureShape.class);
    String recName = ErlangRestXmlSupport.recordName(sp.toSymbol(errShape));
    int status =
        errShape.hasTrait(HttpErrorTrait.class)
            ? errShape.expectTrait(HttpErrorTrait.class).getCode()
            : 500;
    String rootElement = BeamXmlBindingIndex.shapeElementName(errShape);

    Expression body =
        BlockExpr.commaSeparated(
            List.of(
                MatchExpr.bindValue("XmlNs", LocalCallExpr.of("xml_namespace", List.of())),
                MatchExpr.bindValue(
                    "MemberMap", buildStructureXmlMapExpr(model, errShape, "Error", recName)),
                MatchExpr.bindValue(
                    "Inner",
                    LocalCallExpr.of(
                        "encode_xml",
                        List.of(
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(
                                        BinaryExpr.of(rootElement), Variable.of("MemberMap")))),
                            Variable.of("XmlNs")))),
                MatchExpr.bindValue(
                    "Body",
                    LocalCallExpr.of(
                        "encode_xml",
                        List.of(
                            MapExpr.of(
                                List.of(MapEntry.of(BinaryExpr.of("Error"), Variable.of("Inner")))),
                            Variable.of("XmlNs")))),
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
                                            BinaryExpr.of("application/xml")))))),
                        RecordField.of("body", Variable.of("Body"))))),
            false);

    return Function.of(
        "encode_" + recName + "_response",
        List.of(FunctionClause.of(List.of(RecordPattern.of(recName, List.of())), body)));
  }

  static List<Expression> buildDecodeRequestBodyExprs(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(input));

    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
    List<HttpBinding> queryParams =
        httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
    List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> prefixHeaders =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> payloadMembers =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

    List<Expression> body = new ArrayList<>();
    for (HttpBinding lb : labels) {
      String fieldName = BeamNameUtils.toSnakeCase(lb.getMember().getMemberName());
      body.add(
          MatchExpr.bindValue(
              ErlangRestXmlSupport.toBindingVar(fieldName),
              RemoteCallExpr.of(
                  "maps",
                  "get",
                  List.of(
                      BinaryExpr.of(lb.getLocationName()),
                      Variable.of("Labels"),
                      AtomExpr.of("undefined")))));
    }
    for (HttpBinding qb : queries) {
      String fieldName = BeamNameUtils.toSnakeCase(qb.getMember().getMemberName());
      body.add(
          MatchExpr.bindValue(
              ErlangRestXmlSupport.toBindingVar(fieldName),
              RemoteCallExpr.of(
                  "maps",
                  "get",
                  List.of(
                      BinaryExpr.of(qb.getLocationName()),
                      Variable.of("Query"),
                      AtomExpr.of("undefined")))));
    }
    for (HttpBinding hb : headers) {
      body.add(headerBindingDecodeExpr(model, sp, hb));
    }
    for (HttpBinding ph : prefixHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
      body.add(
          MatchExpr.bindValue(
              ErlangRestXmlSupport.toBindingVar(fieldName),
              LocalCallExpr.of(
                  "prefix_headers_from_list",
                  List.of(Variable.of("Headers"), BinaryExpr.of(ph.getLocationName())))));
    }
    body.addAll(payloadDecodeFieldExprs(model, payloadMembers, sp));

    List<RecordField> recordFields = new ArrayList<>();
    for (HttpBinding b :
        ErlangRestXmlSupport.concat(
            labels, queries, queryParams, headers, prefixHeaders, payloadMembers)) {
      String fieldName = BeamNameUtils.toSnakeCase(b.getMember().getMemberName());
      recordFields.add(
          RecordField.of(fieldName, Variable.of(ErlangRestXmlSupport.toBindingVar(fieldName))));
    }
    body.add(TupleExpr.of(List.of(AtomExpr.of("ok"), RecordExpr.of(inputRecord, recordFields))));
    return body;
  }

  static List<Expression> buildDecodeResponseSuccessBodyExprs(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(output));

    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    Set<String> httpBoundMembers = new LinkedHashSet<>();
    for (HttpBinding binding :
        ErlangRestXmlSupport.concat(respHeaders, respPrefixHeaders, respPayload)) {
      httpBoundMembers.add(binding.getMember().getMemberName());
    }
    List<MemberShape> xmlBodyMembers =
        output.members().stream()
            .filter(member -> !httpBoundMembers.contains(member.getMemberName()))
            .toList();

    List<Expression> body = new ArrayList<>();
    for (HttpBinding hb : respHeaders) {
      body.add(headerBindingDecodeExpr(model, sp, hb));
    }
    for (HttpBinding ph : respPrefixHeaders) {
      String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
      body.add(
          MatchExpr.bindValue(
              ErlangRestXmlSupport.toBindingVar(fieldName),
              LocalCallExpr.of(
                  "prefix_headers_from_list",
                  List.of(Variable.of("Headers"), BinaryExpr.of(ph.getLocationName())))));
    }

    if (!respPayload.isEmpty()) {
      body.addAll(payloadBindingDecodeExprs(model, respPayload.get(0), sp));
    } else if (!xmlBodyMembers.isEmpty()) {
      String rootElement = BeamXmlBindingIndex.shapeElementName(output);
      body.add(
          MatchExpr.bindValue(
              "Parsed",
              CaseExpr.of(
                  LocalCallExpr.of(
                      "parse_xml_root", List.of(Variable.of("Body"), BinaryExpr.of(rootElement))),
                  List.of(
                      Clause.of(
                          TuplePattern.of(
                              List.of(AtomPattern.of("ok"), VariablePattern.of("Root"))),
                          Variable.of("Root")),
                      Clause.of(
                          TuplePattern.of(
                              List.of(AtomPattern.of("error"), VariablePattern.of("_"))),
                          AtomExpr.of("undefined"))))));
      body.addAll(buildMembersFromXmlExprs(model, xmlBodyMembers, "Parsed", sp));
    }

    Set<String> boundFields = new LinkedHashSet<>();
    List<RecordField> recordFields = new ArrayList<>();
    for (HttpBinding hb :
        ErlangRestXmlSupport.concat(respHeaders, respPrefixHeaders, respPayload)) {
      String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
      if (boundFields.add(fieldName)) {
        recordFields.add(
            RecordField.of(fieldName, Variable.of(ErlangRestXmlSupport.toBindingVar(fieldName))));
      }
    }
    if (respPayload.isEmpty() && !xmlBodyMembers.isEmpty()) {
      for (MemberShape member : xmlBodyMembers) {
        String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
        if (boundFields.add(fieldName)) {
          recordFields.add(
              RecordField.of(fieldName, Variable.of(ErlangRestXmlSupport.toBindingVar(fieldName))));
        }
      }
    }

    TupleExpr success =
        TupleExpr.of(List.of(AtomExpr.of("ok"), RecordExpr.of(outputRecord, recordFields)));
    body.add(ErlangHttpChecksumIr.responseChecksumGuardExpr(model, op, success));
    return body;
  }

  static Expression buildDecodeResponseFallbackExprs(OperationShape op, SymbolProvider sp) {
    String opName = sp.toSymbol(op).getName();
    if (op.getErrors().isEmpty()) {
      return LocalCallExpr.of(
          "decode_rest_xml_error", List.of(Variable.of("Status"), Variable.of("Body")));
    }
    return LocalCallExpr.of(
        "decode_" + opName + "_response_error",
        List.of(Variable.of("Status"), Variable.of("Body")));
  }

  static List<Expression> buildEncodeRequestBodyExprs(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      boolean encodeWithConfig) {
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(input));
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
    List<HttpBinding> payloadMembers =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

    String requestContentType = resolvedRequestContentType(model, op, payloadMembers);

    List<Expression> body = new ArrayList<>();
    body.addAll(buildIdempotencyTokenExprs(input, inputRecord));

    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    boolean hasHostLabels =
        !hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class);
    BeamS3CustomizationIndex s3Index = BeamS3CustomizationIndex.of(model);
    boolean s3BucketAddressing =
        s3Index.isS3Service(service) && s3Index.bucketLabelBinding(op).isPresent();

    if (s3BucketAddressing) {
      String bucketVar = ErlangRestXmlSupport.toBindingVar(s3Index.bucketMemberSnakeCase(op));
      String keyVar =
          s3Index.keyMemberSnakeCase(op).map(ErlangRestXmlSupport::toBindingVar).orElse("<<>>");
      if (encodeWithConfig) {
        body.add(
            MatchExpr.of(
                TuplePattern.of(List.of(VariablePattern.of("Host"), VariablePattern.of("Path"))),
                RemoteCallExpr.of(
                    "s3_endpoint",
                    "resolve_bucket_url",
                    List.of(Variable.of("Config"), Variable.of(bucketVar), Variable.of(keyVar))),
                null));
      } else {
        body.add(
            MatchExpr.of(
                TuplePattern.of(List.of(VariablePattern.of("Host"), VariablePattern.of("Path"))),
                RemoteCallExpr.of(
                    "s3_endpoint",
                    "resolve_bucket_url",
                    List.of(MapExpr.of(List.of()), Variable.of(bucketVar), Variable.of(keyVar))),
                null));
      }
    } else {
      body.add(MatchExpr.bindValue("Path", buildPathExpression(uriTemplate, labels)));
    }

    if (queries.isEmpty()) {
      body.add(MatchExpr.bindValue("Query", ListExpr.of(List.of())));
    } else {
      body.add(MatchExpr.bindValue("Query", flattenBindingCasesExpr(model, sp, queries, true)));
    }

    body.addAll(buildQueryParamsExprs(queryParams));

    if (headers.isEmpty()) {
      body.add(
          MatchExpr.bindValue(
              "Headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              BinaryExpr.of("Content-Type"),
                              BinaryExpr.of(requestContentType)))))));
    } else {
      body.add(MatchExpr.bindValue("Headers0", flattenBindingCasesExpr(model, sp, headers, false)));
      body.add(
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
                          Variable.of(ErlangRestXmlSupport.toBindingVar(fieldName)))))));
    }

    body.addAll(buildRequestBodyExprs(model, payloadMembers, method, sp));
    ErlangHttpChecksumIr.requestChecksumHeadersExpr(model, op, sp, "Headers").ifPresent(body::add);

    String requestHeaders =
        BeamHttpChecksumIndex.of(model).requestChecksums(op).isEmpty()
            ? "Headers"
            : "HeadersWithChecksum";

    if (hasHostLabels && !s3BucketAddressing) {
      body.add(
          MatchExpr.bindValue(
              "Host",
              LocalCallExpr.of(
                  "build_host", List.of(Variable.of("Input"), Variable.of("Config")))));
    }

    List<RecordField> requestFields = new ArrayList<>();
    requestFields.add(RecordField.of("method", BinaryExpr.of(method)));
    requestFields.add(RecordField.of("path", Variable.of("Path")));
    requestFields.add(
        RecordField.of(
            "query", RemoteCallExpr.of("maps", "from_list", List.of(Variable.of("Query")))));
    requestFields.add(RecordField.of("headers", Variable.of(requestHeaders)));
    requestFields.add(RecordField.of("body", Variable.of("Body")));
    if (hasHostLabels || s3BucketAddressing) {
      requestFields.add(RecordField.of("host", Variable.of("Host")));
    }
    body.add(RecordExpr.of("http_request", requestFields));
    return body;
  }

  static List<Expression> buildEncodeResponseBodyExprs(
      Model model, OperationShape op, HttpBindingIndex httpIndex, SymbolProvider sp) {
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    int successCode = httpIndex.getResponseCode(op);

    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    boolean implicitBody = respPayload.isEmpty() && !output.members().isEmpty();

    List<Expression> body = new ArrayList<>();
    if (respHeaders.isEmpty()) {
      body.add(
          MatchExpr.bindValue(
              "Headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              BinaryExpr.of("Content-Type"), BinaryExpr.of("application/xml")))))));
    } else {
      body.add(
          MatchExpr.bindValue(
              "ExtraHeaders", flattenBindingCasesExpr(model, sp, respHeaders, false)));
      body.add(
          MatchExpr.bindValue(
              "Headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              BinaryExpr.of("Content-Type"), BinaryExpr.of("application/xml")))),
                  Variable.of("ExtraHeaders"))));
    }

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
                          Variable.of(ErlangRestXmlSupport.toBindingVar(fieldName)))))));
    }

    if (!respPayload.isEmpty()) {
      body.addAll(buildResponseBodyFromPayloadExprs(model, respPayload.get(0), sp));
    } else if (implicitBody) {
      String rootElement = BeamXmlBindingIndex.shapeElementName(output);
      body.add(MatchExpr.bindValue("XmlNs", LocalCallExpr.of("xml_namespace", List.of())));
      List<MapEntry> entries = new ArrayList<>();
      for (MemberShape member : output.members()) {
        String field = BeamNameUtils.toSnakeCase(member.getMemberName());
        String wireName = BeamXmlBindingIndex.memberElementName(member);
        entries.add(
            MapEntry.of(
                BinaryExpr.of(wireName), Variable.of(ErlangRestXmlSupport.toBindingVar(field))));
      }
      body.add(
          MatchExpr.bindValue(
              "MemberMap",
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
              "Body",
              LocalCallExpr.of(
                  "encode_xml",
                  List.of(
                      MapExpr.of(
                          List.of(
                              MapEntry.of(BinaryExpr.of(rootElement), Variable.of("MemberMap")))),
                      Variable.of("XmlNs")))));
    } else {
      body.add(MatchExpr.bindValue("Body", BinaryExpr.of("")));
    }

    body.add(
        RecordExpr.of(
            "http_response",
            List.of(
                RecordField.of("status", IntegerExpr.of(successCode)),
                RecordField.of("headers", Variable.of("Headers")),
                RecordField.of("body", Variable.of("Body")))));
    return body;
  }

  private static MatchExpr headerBindingDecodeExpr(
      Model model, SymbolProvider sp, HttpBinding binding) {
    String fieldName = BeamNameUtils.toSnakeCase(binding.getMember().getMemberName());
    String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);
    Expression headerLookup =
        RemoteCallExpr.of(
            "proplists",
            "get_value",
            List.of(
                BinaryExpr.of(binding.getLocationName()),
                Variable.of("Headers"),
                AtomExpr.of("undefined")));
    Shape target = model.expectShape(binding.getMember().getTarget());
    Expression value = headerLookup;
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = sp.toSymbol(target).getName().replace("()", "");
      value = LocalCallExpr.of("decode_" + helperName, List.of(headerLookup));
    }
    return MatchExpr.bindValue(bindingVar, value);
  }

  private static List<Expression> payloadDecodeFieldExprs(
      Model model, List<HttpBinding> payloadMembers, SymbolProvider sp) {
    if (payloadMembers.isEmpty()) {
      return List.of();
    }
    HttpBinding payload = payloadMembers.get(0);
    MemberShape member = payload.getMember();
    Shape target = model.expectShape(member.getTarget());
    String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
    String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);

    if (target instanceof BlobShape || target instanceof StringShape) {
      return List.of(MatchExpr.bindValue(bindingVar, Variable.of("Body")));
    }

    String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
    Expression decodedValue =
        CaseExpr.of(
            LocalCallExpr.of(
                "parse_xml_root", List.of(Variable.of("Body"), BinaryExpr.of(rootElement))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Root"))),
                    payloadDecodeValueExpr(model, target, sp)),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("_"))),
                    AtomExpr.of("undefined"))));

    return List.of(
        MatchExpr.bindValue(
            bindingVar,
            CaseExpr.of(
                Variable.of("Body"),
                List.of(
                    Clause.of(BinaryPattern.of(""), AtomExpr.of("undefined")),
                    Clause.of(WildcardPattern.of(), decodedValue)))));
  }

  private static List<Expression> payloadBindingDecodeExprs(
      Model model, HttpBinding payload, SymbolProvider sp) {
    MemberShape member = payload.getMember();
    Shape target = model.expectShape(member.getTarget());
    String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
    String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);

    if (target instanceof BlobShape || target instanceof StringShape) {
      return List.of(MatchExpr.bindValue(bindingVar, Variable.of("Body")));
    }

    String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
    return List.of(
        MatchExpr.bindValue(
            bindingVar,
            CaseExpr.of(
                LocalCallExpr.of(
                    "parse_xml_root", List.of(Variable.of("Body"), BinaryExpr.of(rootElement))),
                List.of(
                    Clause.of(
                        TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Root"))),
                        payloadDecodeValueExpr(model, target, sp)),
                    Clause.of(
                        TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("_"))),
                        AtomExpr.of("undefined"))))));
  }

  private static Expression payloadDecodeValueExpr(Model model, Shape target, SymbolProvider sp) {
    if (target instanceof StructureShape structure) {
      return buildDecodeStructureExpr(model, structure, "Root", sp);
    }
    if (target instanceof UnionShape union) {
      return buildDecodeUnionExpr(model, union, "Root", sp);
    }
    return LocalCallExpr.of("xml_child_text", List.of(Variable.of("Root"), BinaryExpr.of("")));
  }

  private static Expression buildDecodeStructureExpr(
      Model model, StructureShape structure, String xmlVar, SymbolProvider sp) {
    String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
    List<RecordField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      Shape target = model.expectShape(member.getTarget());
      if (BeamXmlBindingIndex.isXmlAttribute(member)) {
        fields.add(
            RecordField.of(
                field,
                LocalCallExpr.of(
                    "xml_attribute",
                    List.of(
                        Variable.of(xmlVar),
                        BinaryExpr.of(BeamXmlBindingIndex.memberElementName(member))))));
      } else if (target instanceof ListShape listShape) {
        fields.add(
            RecordField.of(field, buildDecodeListFieldExpr(model, member, listShape, xmlVar, sp)));
      } else {
        fields.add(
            RecordField.of(
                field,
                LocalCallExpr.of(
                    "xml_child_text",
                    List.of(
                        Variable.of(xmlVar),
                        BinaryExpr.of(BeamXmlBindingIndex.memberElementName(member))))));
      }
    }
    if (fields.isEmpty()) {
      return RecordExpr.of(recordTag, List.of());
    }
    return RecordExpr.of(recordTag, fields);
  }

  private static Expression buildDecodeUnionExpr(
      Model model, UnionShape union, String xmlVar, SymbolProvider sp) {
    return buildUnionDecodeCase(model, union, xmlVar, sp, 0);
  }

  private static Expression buildUnionDecodeCase(
      Model model, UnionShape union, String xmlVar, SymbolProvider sp, int memberIndex) {
    List<MemberShape> members = new ArrayList<>(union.members());
    if (memberIndex >= members.size()) {
      return AtomExpr.of("undefined");
    }
    MemberShape member = members.get(memberIndex);
    String element = BeamXmlBindingIndex.memberElementName(member);
    String tag = unionTagForMember(sp, member);
    Expression valueExpr = decodeUnionMemberValue(model, member, "Element", sp);
    Expression nextArm = buildUnionDecodeCase(model, union, xmlVar, sp, memberIndex + 1);
    return CaseExpr.of(
        LocalCallExpr.of(
            "find_element",
            List.of(
                BinaryExpr.of(element),
                LocalCallExpr.of("element_content", List.of(Variable.of(xmlVar))))),
        List.of(
            Clause.of(AtomPattern.of("undefined"), nextArm),
            Clause.of(
                VariablePattern.of("Element"),
                TupleExpr.of(List.of(AtomExpr.of(tag), valueExpr)))));
  }

  private static Expression decodeUnionMemberValue(
      Model model, MemberShape member, String elementVar, SymbolProvider sp) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof StructureShape structure) {
      return buildDecodeStructureExpr(model, structure, elementVar, sp);
    }
    if (target instanceof ListShape listShape) {
      String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
      Shape listMember = model.expectShape(listShape.getMember().getTarget());
      if (listMember instanceof StructureShape nested) {
        Fun decodeFun =
            Fun.of(
                List.of(
                    FunClause.of(
                        VariablePattern.of("Item"),
                        buildDecodeStructureExpr(model, nested, "Item", sp))));
        return LocalCallExpr.of(
            "xml_child_struct_list",
            List.of(
                Variable.of(elementVar),
                AtomExpr.of("undefined"),
                BinaryExpr.of(itemElement),
                decodeFun));
      }
      return LocalCallExpr.of(
          "xml_child_list",
          List.of(Variable.of(elementVar), AtomExpr.of("undefined"), BinaryExpr.of(itemElement)));
    }
    return CaseExpr.of(
        LocalCallExpr.of("element_text", List.of(Variable.of(elementVar))),
        List.of(
            Clause.of(ListPattern.of(List.of()), AtomExpr.of("undefined")),
            Clause.of(
                VariablePattern.of("Text"),
                LocalCallExpr.of("list_to_binary", List.of(Variable.of("Text"))))));
  }

  private static Expression buildDecodeListFieldExpr(
      Model model, MemberShape member, ListShape listShape, String xmlVar, SymbolProvider sp) {
    String element = BeamXmlBindingIndex.memberElementName(member);
    String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
    Expression listNameExpr =
        BeamXmlBindingIndex.isContainerMemberFlattened(member)
            ? AtomExpr.of("undefined")
            : BinaryExpr.of(element);
    Shape listMember = model.expectShape(listShape.getMember().getTarget());
    if (listMember instanceof StructureShape nested) {
      Fun decodeFun =
          Fun.of(
              List.of(
                  FunClause.of(
                      VariablePattern.of("Item"),
                      buildDecodeStructureExpr(model, nested, "Item", sp))));
      return LocalCallExpr.of(
          "xml_child_struct_list",
          List.of(Variable.of(xmlVar), listNameExpr, BinaryExpr.of(itemElement), decodeFun));
    }
    return LocalCallExpr.of(
        "xml_child_list", List.of(Variable.of(xmlVar), listNameExpr, BinaryExpr.of(itemElement)));
  }

  private static List<Expression> buildMembersFromXmlExprs(
      Model model, Iterable<MemberShape> members, String xmlVar, SymbolProvider sp) {
    List<Expression> exprs = new ArrayList<>();
    for (MemberShape member : members) {
      exprs.add(buildMemberFromXmlExpr(model, member, xmlVar, sp));
    }
    return exprs;
  }

  private static MatchExpr buildMemberFromXmlExpr(
      Model model, MemberShape member, String xmlVar, SymbolProvider sp) {
    String field = BeamNameUtils.toSnakeCase(member.getMemberName());
    String bindingVar = ErlangRestXmlSupport.toBindingVar(field);
    Shape target = model.expectShape(member.getTarget());
    if (BeamXmlBindingIndex.isXmlAttribute(member)) {
      return MatchExpr.bindValue(
          bindingVar,
          CaseExpr.of(
              Variable.of(xmlVar),
              List.of(
                  Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                  Clause.of(
                      WildcardPattern.of(),
                      LocalCallExpr.of(
                          "xml_attribute",
                          List.of(
                              Variable.of(xmlVar),
                              BinaryExpr.of(BeamXmlBindingIndex.memberElementName(member))))))));
    }
    if (target instanceof ListShape listShape) {
      return MatchExpr.bindValue(
          bindingVar,
          CaseExpr.of(
              Variable.of(xmlVar),
              List.of(
                  Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                  Clause.of(
                      WildcardPattern.of(),
                      buildDecodeListFieldExpr(model, member, listShape, xmlVar, sp)))));
    }
    if (target instanceof StructureShape nested) {
      String element = BeamXmlBindingIndex.memberElementName(member);
      String nestedVar = bindingVar + "_xml";
      return MatchExpr.bindValue(
          bindingVar,
          CaseExpr.of(
              Variable.of(xmlVar),
              List.of(
                  Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                  Clause.of(
                      WildcardPattern.of(),
                      CaseExpr.of(
                          LocalCallExpr.of(
                              "find_element",
                              List.of(
                                  BinaryExpr.of(element),
                                  LocalCallExpr.of(
                                      "element_content", List.of(Variable.of(xmlVar))))),
                          List.of(
                              Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                              Clause.of(
                                  VariablePattern.of(nestedVar),
                                  buildDecodeStructureExpr(model, nested, nestedVar, sp))))))));
    }
    return MatchExpr.bindValue(
        bindingVar,
        CaseExpr.of(
            Variable.of(xmlVar),
            List.of(
                Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                Clause.of(
                    WildcardPattern.of(),
                    LocalCallExpr.of(
                        "xml_child_text",
                        List.of(
                            Variable.of(xmlVar),
                            BinaryExpr.of(BeamXmlBindingIndex.memberElementName(member))))))));
  }

  private static List<Expression> buildRequestBodyExprs(
      Model model, List<HttpBinding> payloadMembers, String method, SymbolProvider sp) {
    List<Expression> exprs = new ArrayList<>();
    if (payloadMembers.isEmpty()
        || method.equals("GET")
        || method.equals("DELETE")
        || method.equals("HEAD")) {
      exprs.add(MatchExpr.bindValue("Body", BinaryExpr.of("")));
      return exprs;
    }

    HttpBinding payload = payloadMembers.get(0);
    MemberShape member = payload.getMember();
    Shape target = model.expectShape(member.getTarget());
    String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
    String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);

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

    String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
    Expression payloadValueExpr;
    if (target instanceof StructureShape structure) {
      String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
      payloadValueExpr =
          BlockExpr.commaSeparated(
              List.of(
                  MatchExpr.bindValue(
                      "MemberMap",
                      buildStructureXmlMapExpr(model, structure, "PayloadValue", recordTag)),
                  LocalCallExpr.of(
                      "encode_xml",
                      List.of(
                          MapExpr.of(
                              List.of(
                                  MapEntry.of(
                                      BinaryExpr.of(rootElement), Variable.of("MemberMap")))),
                          Variable.of("XmlNs")))),
              false);
    } else if (target instanceof UnionShape union) {
      payloadValueExpr = buildUnionPayloadEncodeExpr(model, union, rootElement, "PayloadValue", sp);
    } else {
      payloadValueExpr =
          LocalCallExpr.of(
              "encode_xml",
              List.of(
                  MapExpr.of(
                      List.of(
                          MapEntry.of(BinaryExpr.of(rootElement), Variable.of("PayloadValue")))),
                  Variable.of("XmlNs")));
    }

    exprs.add(MatchExpr.bindValue("XmlNs", LocalCallExpr.of("xml_namespace", List.of())));
    exprs.add(
        MatchExpr.bindValue(
            "Body",
            CaseExpr.of(
                Variable.of(bindingVar),
                List.of(
                    Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")),
                    Clause.of(VariablePattern.of("PayloadValue"), payloadValueExpr)))));
    return exprs;
  }

  private static List<Expression> buildResponseBodyFromPayloadExprs(
      Model model, HttpBinding payload, SymbolProvider sp) {
    MemberShape member = payload.getMember();
    Shape target = model.expectShape(member.getTarget());
    String bindingVar =
        ErlangRestXmlSupport.toBindingVar(BeamNameUtils.toSnakeCase(member.getMemberName()));

    if (target instanceof BlobShape || target instanceof StringShape) {
      return List.of(
          MatchExpr.bindValue(
              "Body",
              CaseExpr.of(
                  Variable.of(bindingVar),
                  List.of(
                      Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")),
                      Clause.of(VariablePattern.of("Value"), Variable.of("Value"))))));
    }

    String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
    Expression payloadValueExpr;
    if (target instanceof StructureShape structure) {
      String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
      payloadValueExpr =
          LocalCallExpr.of(
              "encode_xml",
              List.of(
                  MapExpr.of(
                      List.of(
                          MapEntry.of(
                              BinaryExpr.of(rootElement),
                              buildStructureXmlMapExpr(
                                  model, structure, "PayloadValue", recordTag)))),
                  Variable.of("XmlNs")));
    } else if (target instanceof UnionShape union) {
      payloadValueExpr = buildUnionPayloadEncodeExpr(model, union, rootElement, "PayloadValue", sp);
    } else {
      payloadValueExpr =
          LocalCallExpr.of(
              "encode_xml",
              List.of(
                  MapExpr.of(
                      List.of(
                          MapEntry.of(BinaryExpr.of(rootElement), Variable.of("PayloadValue")))),
                  Variable.of("XmlNs")));
    }

    return List.of(
        MatchExpr.bindValue("XmlNs", LocalCallExpr.of("xml_namespace", List.of())),
        MatchExpr.bindValue(
            "Body",
            CaseExpr.of(
                Variable.of(bindingVar),
                List.of(
                    Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")),
                    Clause.of(VariablePattern.of("PayloadValue"), payloadValueExpr)))));
  }

  private static Expression buildUnionPayloadEncodeExpr(
      Model model, UnionShape union, String rootElement, String valueVar, SymbolProvider sp) {
    List<Clause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      String element = BeamXmlBindingIndex.memberElementName(member);
      String tag = unionTagForMember(sp, member);
      Shape memberTarget = model.expectShape(member.getTarget());
      Expression innerValue;
      if (memberTarget instanceof StructureShape structure) {
        String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
        innerValue = buildStructureXmlMapExpr(model, structure, "V", recordTag);
      } else {
        innerValue = Variable.of("V");
      }
      clauses.add(
          Clause.of(
              TuplePattern.of(List.of(AtomPattern.of(tag), VariablePattern.of("V"))),
              LocalCallExpr.of(
                  "encode_xml",
                  List.of(
                      MapExpr.of(
                          List.of(
                              MapEntry.of(
                                  BinaryExpr.of(rootElement),
                                  MapExpr.of(
                                      List.of(MapEntry.of(BinaryExpr.of(element), innerValue)))))),
                      Variable.of("XmlNs")))));
    }
    clauses.add(Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")));
    return CaseExpr.of(Variable.of(valueVar), clauses);
  }

  private static Expression buildStructureXmlMapExpr(
      Model model, StructureShape structure, String recordVar, String recordTag) {
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      if (BeamXmlBindingIndex.isXmlAttribute(member)) {
        continue;
      }
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      String wireName = BeamXmlBindingIndex.memberElementName(member);
      entries.add(
          MapEntry.of(
              BinaryExpr.of(wireName),
              RecordFieldAccessExpr.of(Variable.of(recordVar), recordTag, field)));
    }
    if (entries.isEmpty()) {
      return MapExpr.of(List.of());
    }
    return MapExpr.of(entries);
  }

  private static Expression flattenBindingCasesExpr(
      Model model, SymbolProvider sp, List<HttpBinding> bindings, boolean queryValues) {
    List<Expression> cases = new ArrayList<>();
    for (HttpBinding binding : bindings) {
      String fieldVar =
          ErlangRestXmlSupport.toBindingVar(
              BeamNameUtils.toSnakeCase(binding.getMember().getMemberName()));
      String valueVar = fieldVar + "Val";
      Expression encodedValue =
          encodeBindingWireValueExpr(model, sp, binding.getMember(), valueVar, queryValues);
      cases.add(
          CaseExpr.of(
              Variable.of(fieldVar),
              List.of(
                  Clause.of(AtomPattern.of("undefined"), ListExpr.of(List.of())),
                  Clause.of(
                      VariablePattern.of(valueVar),
                      ListExpr.of(
                          List.of(
                              TupleExpr.of(
                                  List.of(
                                      BinaryExpr.of(binding.getLocationName()),
                                      encodedValue))))))));
    }
    return RemoteCallExpr.of("lists", "flatten", List.of(ListExpr.of(cases)));
  }

  private static Expression encodeBindingWireValueExpr(
      Model model, SymbolProvider sp, MemberShape member, String valueVar, boolean queryValues) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = sp.toSymbol(target).getName().replace("()", "");
      return LocalCallExpr.of("encode_" + helperName, List.of(Variable.of(valueVar)));
    }
    if (queryValues) {
      return LocalCallExpr.of("encode_query_value", List.of(Variable.of(valueVar)));
    }
    return LocalCallExpr.of("to_binary", List.of(Variable.of(valueVar)));
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
              ErlangRestXmlSupport.toBindingVar(field),
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
                          "to_binary",
                          List.of(Variable.of(ErlangRestXmlSupport.toBindingVar(fieldName)))))),
              "binary"));
      pos = end + 1;
    }
    return BinaryExpr.of(segments);
  }

  private static List<Expression> buildQueryParamsExprs(List<HttpBinding> queryParams) {
    if (queryParams.isEmpty()) {
      return List.of();
    }
    List<Expression> exprs = new ArrayList<>();
    for (HttpBinding qp : queryParams) {
      String fieldName = BeamNameUtils.toSnakeCase(qp.getMember().getMemberName());
      String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);
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

  private static String unionTagForMember(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
  }

  private static String resolvedRequestContentType(
      Model model, OperationShape op, List<HttpBinding> payloadMembers) {
    if (!payloadMembers.isEmpty()) {
      MemberShape member = payloadMembers.get(0).getMember();
      Shape target = model.expectShape(member.getTarget());
      Optional<String> mediaType =
          member.getTrait(MediaTypeTrait.class).map(MediaTypeTrait::getValue);
      if (mediaType.isPresent()) {
        return mediaType.get();
      }
      if (target instanceof BlobShape) {
        return "application/octet-stream";
      }
      if (target instanceof StringShape) {
        return "text/plain";
      }
    }
    return BeamHttpBindings.from(model)
        .requestContentType(op, DEFAULT_CONTENT_TYPE)
        .orElse(DEFAULT_CONTENT_TYPE);
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
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

    List<RecordPatternField> fields = new ArrayList<>();
    if (respPayload.isEmpty() && !output.members().isEmpty()) {
      for (MemberShape member : output.members()) {
        addBindingField(fields, member.getMemberName());
      }
    } else {
      for (HttpBinding binding :
          ErlangRestXmlSupport.concat(respHeaders, respPrefixHeaders, respPayload)) {
        addBindingField(fields, binding.getMember().getMemberName());
      }
    }
    return RecordPattern.of(outputRecord, fields);
  }

  private static void addBindingField(List<RecordPatternField> fields, String memberName) {
    String field = BeamNameUtils.toSnakeCase(memberName);
    fields.add(
        RecordPatternField.of(field, VariablePattern.of(ErlangRestXmlSupport.toBindingVar(field))));
  }

  private static RecordPattern recordBindingHead(
      String alias, String recordName, List<HttpBinding> bindings) {
    List<RecordPatternField> fields = new ArrayList<>();
    for (HttpBinding binding : bindings) {
      addBindingField(fields, binding.getMember().getMemberName());
    }
    return RecordPattern.bind(alias, recordName, fields);
  }

  private static RecordPattern httpRequestPattern() {
    return RecordPattern.of(
        "http_request",
        List.of(
            RecordPatternField.of("query", VariablePattern.of("Query")),
            RecordPatternField.of("headers", VariablePattern.of("Headers")),
            RecordPatternField.of("body", VariablePattern.of("Body"))));
  }
}
