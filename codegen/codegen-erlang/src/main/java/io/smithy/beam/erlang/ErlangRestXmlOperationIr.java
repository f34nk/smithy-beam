package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlBinarySegment;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlIntegerPattern;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlPattern;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRecordAccess;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ErlangRestXmlOperationIr {
    private static final String DEFAULT_CONTENT_TYPE = "application/xml";

    private ErlangRestXmlOperationIr() {}

    static ErlFunction buildEncodeRequest(
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
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> payloadMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
        List<HttpBinding> patternBindings =
                ErlangRestXmlSupport.concat(labels, queries, queryParams, headers, prefixHeaders, payloadMembers);

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "encode_" + opName + "_request",
                encodeWithConfig ? "client_config(), " + inputType : inputType,
                "#http_request{}");
        List<ErlPattern> patterns = encodeWithConfig
                ? List.of(
                        ErlVarPattern.varPattern("Config"),
                        recordBindingHead("Input", inputRecord, patternBindings))
                : List.of(recordBindingHead("Input", inputRecord, patternBindings));

        return ErlFunction.functionWithDocAndSpec(
                "encode_" + opName + "_request",
                patterns.size(),
                ErlFunctionDoc.functionDoc("Encode REST-XML request for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(
                        patterns,
                        ErlExprBlock.block(buildEncodeRequestBodyExprs(
                                        model, service, op, httpIndex, sp, encodeWithConfig)
                                .toArray(ErlExpr[]::new)))));
    }

    static ErlFunction buildDecodeRequest(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputType = sp.toSymbol(input).getName();
        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);

        ErlFunctionSpec spec = labels.isEmpty()
                ? ErlFunctionSpec.functionSpec(
                        "decode_" + opName + "_request",
                        "#http_request{}",
                        "{'ok', " + inputType + "} | {'error', term()}")
                : ErlFunctionSpec.functionSpec(
                        "decode_" + opName + "_request",
                        "map(), #http_request{}",
                        "{'ok', " + inputType + "} | {'error', term()}");

        List<ErlPattern> patterns = labels.isEmpty()
                ? List.of(httpRequestPattern())
                : List.of(ErlVarPattern.varPattern("Labels"), httpRequestPattern());

        return ErlFunction.functionWithDocAndSpec(
                "decode_" + opName + "_request",
                patterns.size(),
                ErlFunctionDoc.functionDoc("Decode REST-XML request for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(
                        patterns,
                        ErlExprBlock.block(buildDecodeRequestBodyExprs(model, op, httpIndex, sp)
                                .toArray(ErlExpr[]::new)))));
    }

    static List<ErlFunction> buildDecodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputType = sp.toSymbol(output).getName();
        int successCode = httpIndex.getResponseCode(op);

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "decode_" + opName + "_response",
                "#http_response{}",
                "{'ok', " + outputType + "} | {'error', term()}");

        List<ErlPattern> successPatterns = List.of(
                ErlRecordPattern.recordPattern(
                        "http_response",
                        ErlRecordFieldPattern.fieldPattern("status", ErlIntegerPattern.integerPattern(successCode)),
                        ErlRecordFieldPattern.fieldPattern("headers", ErlVarPattern.varPattern("Headers")),
                        ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body"))));

        List<ErlPattern> fallbackPatterns = List.of(
                ErlRecordPattern.recordPattern(
                        "http_response",
                        ErlRecordFieldPattern.fieldPattern("status", ErlVarPattern.varPattern("Status")),
                        ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body"))));

        List<ErlClause> clauses = new ArrayList<>();
        clauses.add(ErlClause.clause(
                successPatterns,
                ErlExprBlock.block(buildDecodeResponseSuccessBodyExprs(model, op, httpIndex, sp)
                        .toArray(ErlExpr[]::new))));
        clauses.add(ErlClause.clause(
                fallbackPatterns,
                buildDecodeResponseFallbackExprs(op, sp)));

        List<ErlFunction> functions = new ArrayList<>();
        functions.add(ErlFunction.functionWithDocAndSpec(
                "decode_" + opName + "_response",
                1,
                ErlFunctionDoc.functionDoc("Decode REST-XML response for " + op.getId() + "."),
                spec,
                clauses));

        if (!op.getErrors().isEmpty()) {
            functions.add(ErlFunction.functionWithDocAndSpec(
                    "decode_" + opName + "_response_error",
                    2,
                    ErlFunctionDoc.functionDoc("Error dispatch for " + op.getId() + "."),
                    ErlFunctionSpec.functionSpec(
                            "decode_" + opName + "_response_error",
                            "integer(), term()",
                            "{'error', term()}"),
                    ErlangRestXmlSupport.buildResponseErrorDispatchClauses(model, op, sp)));
        }
        return functions;
    }

    static ErlFunction buildEncodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(output));
        String outputType = sp.toSymbol(output).getName();

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "encode_" + opName + "_response", outputType, "#http_response{}");

        return ErlFunction.functionWithDocAndSpec(
                "encode_" + opName + "_response",
                1,
                ErlFunctionDoc.functionDoc("Encode REST-XML response for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(
                        List.of(encodeResponsePattern(model, op, httpIndex, sp, output, outputRecord)),
                        ErlExprBlock.block(buildEncodeResponseBodyExprs(model, op, httpIndex, sp)
                                .toArray(ErlExpr[]::new)))));
    }

    static ErlFunction buildErrorResponseEncoder(Model model, ShapeId errorId, SymbolProvider sp) {
        StructureShape errShape = model.expectShape(errorId, StructureShape.class);
        String recName = ErlangRestXmlSupport.recordName(sp.toSymbol(errShape));
        int status = errShape.hasTrait(HttpErrorTrait.class)
                ? errShape.expectTrait(HttpErrorTrait.class).getCode()
                : 500;
        String rootElement = BeamXmlBindingIndex.shapeElementName(errShape);

        ErlExpr body = ErlExprBlock.block(
                ErlMatch.match(ErlVarPattern.varPattern("XmlNs"), ErlCallLocal.callLocal("xml_namespace")),
                ErlMatch.match(
                        ErlVarPattern.varPattern("MemberMap"),
                        buildStructureXmlMapExpr(model, errShape, "Error", recName)),
                ErlMatch.match(
                        ErlVarPattern.varPattern("Inner"),
                        ErlCallLocal.callLocal(
                                "encode_xml",
                                ErlMap.map(
                                        ErlMapEntry.entry(ErlBinary.binary(rootElement), ErlVar.var("MemberMap"))),
                                ErlVar.var("XmlNs"))),
                ErlMatch.match(
                        ErlVarPattern.varPattern("Body"),
                        ErlCallLocal.callLocal(
                                "encode_xml",
                                ErlMap.map(ErlMapEntry.entry(ErlBinary.binary("Error"), ErlVar.var("Inner"))),
                                ErlVar.var("XmlNs"))),
                ErlRecord.record(
                        "http_response",
                        ErlRecordField.field("status", ErlInteger.integer(status)),
                        ErlRecordField.field(
                                "headers",
                                ErlList.list(ErlTuple.tuple(
                                        ErlBinary.binary("Content-Type"),
                                        ErlBinary.binary("application/xml")))),
                        ErlRecordField.field("body", ErlVar.var("Body"))));

        return ErlFunction.function(
                "encode_" + recName + "_response",
                1,
                List.of(ErlClause.clause(
                        List.of(new ErlRecordPattern(recName, List.of())),
                        body)));
    }

    static List<ErlExpr> buildDecodeRequestBodyExprs(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(input));

        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> payloadMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

        List<ErlExpr> body = new ArrayList<>();
        for (HttpBinding lb : labels) {
            String fieldName = BeamNameUtils.toSnakeCase(lb.getMember().getMemberName());
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern(ErlangRestXmlSupport.toBindingVar(fieldName)),
                    ErlCall.call(
                            "maps",
                            "get",
                            ErlBinary.binary(lb.getLocationName()),
                            ErlVar.var("Labels"),
                            ErlAtom.atom("undefined"))));
        }
        for (HttpBinding qb : queries) {
            String fieldName = BeamNameUtils.toSnakeCase(qb.getMember().getMemberName());
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern(ErlangRestXmlSupport.toBindingVar(fieldName)),
                    ErlCall.call(
                            "maps",
                            "get",
                            ErlBinary.binary(qb.getLocationName()),
                            ErlVar.var("Query"),
                            ErlAtom.atom("undefined"))));
        }
        for (HttpBinding hb : headers) {
            body.add(headerBindingDecodeExpr(model, sp, hb));
        }
        for (HttpBinding ph : prefixHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern(ErlangRestXmlSupport.toBindingVar(fieldName)),
                    ErlCallLocal.callLocal(
                            "prefix_headers_from_list",
                            ErlVar.var("Headers"),
                            ErlBinary.binary(ph.getLocationName()))));
        }
        body.addAll(payloadDecodeFieldExprs(model, payloadMembers, sp));

        List<ErlRecordField> recordFields = new ArrayList<>();
        for (HttpBinding b : ErlangRestXmlSupport.concat(
                labels, queries, queryParams, headers, prefixHeaders, payloadMembers)) {
            String fieldName = BeamNameUtils.toSnakeCase(b.getMember().getMemberName());
            recordFields.add(ErlRecordField.field(
                    fieldName, ErlVar.var(ErlangRestXmlSupport.toBindingVar(fieldName))));
        }
        body.add(ErlTuple.tuple(
                ErlAtom.atom("ok"),
                ErlRecord.record(inputRecord, recordFields.toArray(ErlRecordField[]::new))));
        return body;
    }

    static List<ErlExpr> buildDecodeResponseSuccessBodyExprs(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = ErlangRestXmlSupport.recordName(sp.toSymbol(output));

        List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> respPrefixHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
        Set<String> httpBoundMembers = new LinkedHashSet<>();
        for (HttpBinding binding : ErlangRestXmlSupport.concat(respHeaders, respPrefixHeaders, respPayload)) {
            httpBoundMembers.add(binding.getMember().getMemberName());
        }
        List<MemberShape> xmlBodyMembers = output.members().stream()
                .filter(member -> !httpBoundMembers.contains(member.getMemberName()))
                .toList();

        List<ErlExpr> body = new ArrayList<>();
        for (HttpBinding hb : respHeaders) {
            body.add(headerBindingDecodeExpr(model, sp, hb));
        }
        for (HttpBinding ph : respPrefixHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern(ErlangRestXmlSupport.toBindingVar(fieldName)),
                    ErlCallLocal.callLocal(
                            "prefix_headers_from_list",
                            ErlVar.var("Headers"),
                            ErlBinary.binary(ph.getLocationName()))));
        }

        if (!respPayload.isEmpty()) {
            body.addAll(payloadBindingDecodeExprs(model, respPayload.get(0), sp));
        } else if (!xmlBodyMembers.isEmpty()) {
            String rootElement = BeamXmlBindingIndex.shapeElementName(output);
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Parsed"),
                    ErlCase.caseExpr(
                            ErlCallLocal.callLocal("parse_xml_root", ErlVar.var("Body"), ErlBinary.binary(rootElement)),
                            ErlClause.clause(
                                    List.of(ErlTuplePattern.tuplePattern(
                                            ErlAtomPattern.atomPattern("ok"),
                                            ErlVarPattern.varPattern("Root"))),
                                    ErlVar.var("Root")),
                            ErlClause.clause(
                                    List.of(ErlTuplePattern.tuplePattern(
                                            ErlAtomPattern.atomPattern("error"),
                                            ErlVarPattern.varPattern("_"))),
                                    ErlAtom.atom("undefined")))));
            body.addAll(buildMembersFromXmlExprs(model, xmlBodyMembers, "Parsed", sp));
        }

        Set<String> boundFields = new LinkedHashSet<>();
        List<ErlRecordField> recordFields = new ArrayList<>();
        for (HttpBinding hb : ErlangRestXmlSupport.concat(respHeaders, respPrefixHeaders, respPayload)) {
            String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
            if (boundFields.add(fieldName)) {
                recordFields.add(ErlRecordField.field(
                        fieldName, ErlVar.var(ErlangRestXmlSupport.toBindingVar(fieldName))));
            }
        }
        if (respPayload.isEmpty() && !xmlBodyMembers.isEmpty()) {
            for (MemberShape member : xmlBodyMembers) {
                String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
                if (boundFields.add(fieldName)) {
                    recordFields.add(ErlRecordField.field(
                            fieldName, ErlVar.var(ErlangRestXmlSupport.toBindingVar(fieldName))));
                }
            }
        }

        ErlTuple success = ErlTuple.tuple(
                ErlAtom.atom("ok"),
                ErlRecord.record(outputRecord, recordFields.toArray(ErlRecordField[]::new)));
        body.add(ErlangHttpChecksumIr.responseChecksumGuardExpr(model, op, success));
        return body;
    }

    static ErlExpr buildDecodeResponseFallbackExprs(OperationShape op, SymbolProvider sp) {
        String opName = sp.toSymbol(op).getName();
        if (op.getErrors().isEmpty()) {
            return ErlCallLocal.callLocal(
                    "decode_rest_xml_error", ErlVar.var("Status"), ErlVar.var("Body"));
        }
        return ErlCallLocal.callLocal(
                "decode_" + opName + "_response_error", ErlVar.var("Status"), ErlVar.var("Body"));
    }

    static List<ErlExpr> buildEncodeRequestBodyExprs(
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
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> payloadMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

        String requestContentType = resolvedRequestContentType(model, op, payloadMembers);

        List<ErlExpr> body = new ArrayList<>();
        body.addAll(buildIdempotencyTokenExprs(input, inputRecord));

        BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
        boolean hasHostLabels = !hostLabelIndex.hostLabelMembers(op).isEmpty()
                && op.hasTrait(EndpointTrait.class);
        BeamS3CustomizationIndex s3Index = BeamS3CustomizationIndex.of(model);
        boolean s3BucketAddressing = s3Index.isS3Service(service)
                && s3Index.bucketLabelBinding(op).isPresent();

        if (s3BucketAddressing) {
            String bucketVar = ErlangRestXmlSupport.toBindingVar(s3Index.bucketMemberSnakeCase(op));
            String keyVar = s3Index.keyMemberSnakeCase(op)
                    .map(ErlangRestXmlSupport::toBindingVar)
                    .orElse("<<>>");
            if (encodeWithConfig) {
                body.add(ErlMatch.match(
                        ErlTuplePattern.tuplePattern(
                                ErlVarPattern.varPattern("Host"),
                                ErlVarPattern.varPattern("Path")),
                        ErlCall.call(
                                "s3_endpoint",
                                "resolve_bucket_url",
                                ErlVar.var("Config"),
                                ErlVar.var(bucketVar),
                                ErlVar.var(keyVar))));
            } else {
                body.add(ErlMatch.match(
                        ErlTuplePattern.tuplePattern(
                                ErlVarPattern.varPattern("Host"),
                                ErlVarPattern.varPattern("Path")),
                        ErlCall.call(
                                "s3_endpoint",
                                "resolve_bucket_url",
                                ErlMap.map(),
                                ErlVar.var(bucketVar),
                                ErlVar.var(keyVar))));
            }
        } else {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Path"),
                    buildPathExpression(uriTemplate, labels)));
        }

        if (queries.isEmpty()) {
            body.add(ErlMatch.match(ErlVarPattern.varPattern("Query"), ErlList.list()));
        } else {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Query"),
                    flattenBindingCasesExpr(model, sp, queries, true)));
        }

        body.addAll(buildQueryParamsExprs(queryParams));

        if (headers.isEmpty()) {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlList.list(ErlTuple.tuple(
                            ErlBinary.binary("Content-Type"),
                            ErlBinary.binary(requestContentType)))));
        } else {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers0"),
                    flattenBindingCasesExpr(model, sp, headers, false)));
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlList.cons(
                            ErlTuple.tuple(
                                    ErlBinary.binary("Content-Type"),
                                    ErlBinary.binary(requestContentType)),
                            ErlVar.var("Headers0"))));
        }

        for (HttpBinding ph : prefixHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlOp.op(
                            "++",
                            ErlVar.var("Headers"),
                            ErlCallLocal.callLocal(
                                    "prefix_headers_to_list",
                                    ErlBinary.binary(ph.getLocationName()),
                                    ErlVar.var(ErlangRestXmlSupport.toBindingVar(fieldName))))));
        }

        body.addAll(buildRequestBodyExprs(model, payloadMembers, method, sp));
        ErlangHttpChecksumIr.requestChecksumHeadersExpr(model, op, sp, "Headers").ifPresent(body::add);

        String requestHeaders = BeamHttpChecksumIndex.of(model).requestChecksums(op).isEmpty()
                ? "Headers"
                : "HeadersWithChecksum";

        if (hasHostLabels && !s3BucketAddressing) {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Host"),
                    ErlCallLocal.callLocal("build_host", ErlVar.var("Input"), ErlVar.var("Config"))));
        }

        List<ErlRecordField> requestFields = new ArrayList<>();
        requestFields.add(ErlRecordField.field("method", ErlBinary.binary(method)));
        requestFields.add(ErlRecordField.field("path", ErlVar.var("Path")));
        requestFields.add(ErlRecordField.field(
                "query", ErlCall.call("maps", "from_list", ErlVar.var("Query"))));
        requestFields.add(ErlRecordField.field("headers", ErlVar.var(requestHeaders)));
        requestFields.add(ErlRecordField.field("body", ErlVar.var("Body")));
        if (hasHostLabels || s3BucketAddressing) {
            requestFields.add(ErlRecordField.field("host", ErlVar.var("Host")));
        }
        body.add(ErlRecord.record("http_request", requestFields.toArray(ErlRecordField[]::new)));
        return body;
    }

    static List<ErlExpr> buildEncodeResponseBodyExprs(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        int successCode = httpIndex.getResponseCode(op);

        List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> respPrefixHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
        boolean implicitBody = respPayload.isEmpty() && !output.members().isEmpty();

        List<ErlExpr> body = new ArrayList<>();
        if (respHeaders.isEmpty()) {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlList.list(ErlTuple.tuple(
                            ErlBinary.binary("Content-Type"),
                            ErlBinary.binary("application/xml")))));
        } else {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("ExtraHeaders"),
                    flattenBindingCasesExpr(model, sp, respHeaders, false)));
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlList.cons(
                            ErlTuple.tuple(
                                    ErlBinary.binary("Content-Type"),
                                    ErlBinary.binary("application/xml")),
                            ErlVar.var("ExtraHeaders"))));
        }

        for (HttpBinding ph : respPrefixHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlOp.op(
                            "++",
                            ErlVar.var("Headers"),
                            ErlCallLocal.callLocal(
                                    "prefix_headers_to_list",
                                    ErlBinary.binary(ph.getLocationName()),
                                    ErlVar.var(ErlangRestXmlSupport.toBindingVar(fieldName))))));
        }

        if (!respPayload.isEmpty()) {
            body.addAll(buildResponseBodyFromPayloadExprs(model, respPayload.get(0), sp));
        } else if (implicitBody) {
            String rootElement = BeamXmlBindingIndex.shapeElementName(output);
            body.add(ErlMatch.match(ErlVarPattern.varPattern("XmlNs"), ErlCallLocal.callLocal("xml_namespace")));
            List<ErlMapEntry> entries = new ArrayList<>();
            for (MemberShape member : output.members()) {
                String field = BeamNameUtils.toSnakeCase(member.getMemberName());
                String wireName = BeamXmlBindingIndex.memberElementName(member);
                entries.add(ErlMapEntry.entry(
                        ErlBinary.binary(wireName),
                        ErlVar.var(ErlangRestXmlSupport.toBindingVar(field))));
            }
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("MemberMap"),
                    ErlCall.call(
                            "maps",
                            "filter",
                            ErlFun.fun(ErlClause.clause(
                                    List.of(
                                            ErlVarPattern.varPattern("_"),
                                            ErlVarPattern.varPattern("V")),
                                    ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                            ErlMap.map(entries.toArray(ErlMapEntry[]::new)))));
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Body"),
                    ErlCallLocal.callLocal(
                            "encode_xml",
                            ErlMap.map(ErlMapEntry.entry(
                                    ErlBinary.binary(rootElement),
                                    ErlVar.var("MemberMap"))),
                            ErlVar.var("XmlNs"))));
        } else {
            body.add(ErlMatch.match(ErlVarPattern.varPattern("Body"), ErlBinary.binary("")));
        }

        body.add(ErlRecord.record(
                "http_response",
                ErlRecordField.field("status", ErlInteger.integer(successCode)),
                ErlRecordField.field("headers", ErlVar.var("Headers")),
                ErlRecordField.field("body", ErlVar.var("Body"))));
        return body;
    }

    private static ErlMatch headerBindingDecodeExpr(Model model, SymbolProvider sp, HttpBinding binding) {
        String fieldName = BeamNameUtils.toSnakeCase(binding.getMember().getMemberName());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);
        ErlExpr headerLookup = ErlCall.call(
                "proplists",
                "get_value",
                ErlBinary.binary(binding.getLocationName()),
                ErlVar.var("Headers"),
                ErlAtom.atom("undefined"));
        Shape target = model.expectShape(binding.getMember().getTarget());
        ErlExpr value = headerLookup;
        if (target instanceof EnumShape || target instanceof IntEnumShape) {
            String helperName = sp.toSymbol(target).getName().replace("()", "");
            value = ErlCallLocal.callLocal("decode_" + helperName, headerLookup);
        }
        return ErlMatch.match(ErlVarPattern.varPattern(bindingVar), value);
    }

    private static List<ErlExpr> payloadDecodeFieldExprs(
            Model model,
            List<HttpBinding> payloadMembers,
            SymbolProvider sp) {
        if (payloadMembers.isEmpty()) {
            return List.of();
        }
        HttpBinding payload = payloadMembers.get(0);
        MemberShape member = payload.getMember();
        Shape target = model.expectShape(member.getTarget());
        String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);

        if (target instanceof BlobShape || target instanceof StringShape) {
            return List.of(ErlMatch.match(ErlVarPattern.varPattern(bindingVar), ErlVar.var("Body")));
        }

        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        ErlExpr decodedValue = ErlCase.caseExpr(
                ErlCallLocal.callLocal("parse_xml_root", ErlVar.var("Body"), ErlBinary.binary(rootElement)),
                ErlClause.clause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("ok"),
                                ErlVarPattern.varPattern("Root"))),
                        payloadDecodeValueExpr(model, target, sp)),
                ErlClause.clause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("error"),
                                ErlVarPattern.varPattern("_"))),
                        ErlAtom.atom("undefined")));

        return List.of(ErlMatch.match(
                ErlVarPattern.varPattern(bindingVar),
                ErlCase.caseExpr(
                        ErlVar.var("Body"),
                        ErlClause.clause(List.of(ErlBinaryPattern.binaryPattern("")), ErlAtom.atom("undefined")),
                        ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), decodedValue))));
    }

    private static List<ErlExpr> payloadBindingDecodeExprs(
            Model model,
            HttpBinding payload,
            SymbolProvider sp) {
        MemberShape member = payload.getMember();
        Shape target = model.expectShape(member.getTarget());
        String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);

        if (target instanceof BlobShape || target instanceof StringShape) {
            return List.of(ErlMatch.match(ErlVarPattern.varPattern(bindingVar), ErlVar.var("Body")));
        }

        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        return List.of(ErlMatch.match(
                ErlVarPattern.varPattern(bindingVar),
                ErlCase.caseExpr(
                        ErlCallLocal.callLocal("parse_xml_root", ErlVar.var("Body"), ErlBinary.binary(rootElement)),
                        ErlClause.clause(
                                List.of(ErlTuplePattern.tuplePattern(
                                        ErlAtomPattern.atomPattern("ok"),
                                        ErlVarPattern.varPattern("Root"))),
                                payloadDecodeValueExpr(model, target, sp)),
                        ErlClause.clause(
                                List.of(ErlTuplePattern.tuplePattern(
                                        ErlAtomPattern.atomPattern("error"),
                                        ErlVarPattern.varPattern("_"))),
                                ErlAtom.atom("undefined")))));
    }

    private static ErlExpr payloadDecodeValueExpr(Model model, Shape target, SymbolProvider sp) {
        if (target instanceof StructureShape structure) {
            return buildDecodeStructureExpr(model, structure, "Root", sp);
        }
        if (target instanceof UnionShape union) {
            return buildDecodeUnionExpr(model, union, "Root", sp);
        }
        return ErlCallLocal.callLocal("xml_child_text", ErlVar.var("Root"), ErlBinary.binary(""));
    }

    private static ErlExpr buildDecodeStructureExpr(
            Model model,
            StructureShape structure,
            String xmlVar,
            SymbolProvider sp) {
        String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            Shape target = model.expectShape(member.getTarget());
            if (BeamXmlBindingIndex.isXmlAttribute(member)) {
                fields.add(ErlRecordField.field(
                        field,
                        ErlCallLocal.callLocal(
                                "xml_attribute",
                                ErlVar.var(xmlVar),
                                ErlBinary.binary(BeamXmlBindingIndex.memberElementName(member)))));
            } else if (target instanceof ListShape listShape) {
                fields.add(ErlRecordField.field(
                        field, buildDecodeListFieldExpr(model, member, listShape, xmlVar, sp)));
            } else {
                fields.add(ErlRecordField.field(
                        field,
                        ErlCallLocal.callLocal(
                                "xml_child_text",
                                ErlVar.var(xmlVar),
                                ErlBinary.binary(BeamXmlBindingIndex.memberElementName(member)))));
            }
        }
        if (fields.isEmpty()) {
            return ErlRecord.record(recordTag);
        }
        return ErlRecord.recordCompact(recordTag, fields.toArray(ErlRecordField[]::new));
    }

    private static ErlExpr buildDecodeUnionExpr(
            Model model,
            UnionShape union,
            String xmlVar,
            SymbolProvider sp) {
        return buildUnionDecodeCase(model, union, xmlVar, sp, 0);
    }

    private static ErlExpr buildUnionDecodeCase(
            Model model,
            UnionShape union,
            String xmlVar,
            SymbolProvider sp,
            int memberIndex) {
        List<MemberShape> members = new ArrayList<>(union.members());
        if (memberIndex >= members.size()) {
            return ErlAtom.atom("undefined");
        }
        MemberShape member = members.get(memberIndex);
        String element = BeamXmlBindingIndex.memberElementName(member);
        String tag = unionTagForMember(sp, member);
        ErlExpr valueExpr = decodeUnionMemberValue(model, member, "Element", sp);
        ErlExpr nextArm = buildUnionDecodeCase(model, union, xmlVar, sp, memberIndex + 1);
        return ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "find_element",
                        ErlBinary.binary(element),
                        ErlCallLocal.callLocal("element_content", ErlVar.var(xmlVar))),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), nextArm),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Element")),
                        ErlTuple.tuple(ErlAtom.atom(tag), valueExpr)));
    }

    private static ErlExpr decodeUnionMemberValue(
            Model model,
            MemberShape member,
            String elementVar,
            SymbolProvider sp) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof StructureShape structure) {
            return buildDecodeStructureExpr(model, structure, elementVar, sp);
        }
        if (target instanceof ListShape listShape) {
            String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
            Shape listMember = model.expectShape(listShape.getMember().getTarget());
            if (listMember instanceof StructureShape nested) {
                ErlFun decodeFun = ErlFun.fun(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Item")),
                        buildDecodeStructureExpr(model, nested, "Item", sp)));
                return ErlCallLocal.callLocal(
                        "xml_child_struct_list",
                        ErlVar.var(elementVar),
                        ErlAtom.atom("undefined"),
                        ErlBinary.binary(itemElement),
                        decodeFun);
            }
            return ErlCallLocal.callLocal(
                    "xml_child_list",
                    ErlVar.var(elementVar),
                    ErlAtom.atom("undefined"),
                    ErlBinary.binary(itemElement));
        }
        return ErlCase.caseExpr(
                ErlCallLocal.callLocal("element_text", ErlVar.var(elementVar)),
                ErlClause.clause(List.of(ErlNilPattern.nilPattern()), ErlAtom.atom("undefined")),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Text")),
                        ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Text"))));
    }

    private static ErlExpr buildDecodeListFieldExpr(
            Model model,
            MemberShape member,
            ListShape listShape,
            String xmlVar,
            SymbolProvider sp) {
        String element = BeamXmlBindingIndex.memberElementName(member);
        String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
        ErlExpr listNameExpr = BeamXmlBindingIndex.isContainerMemberFlattened(member)
                ? ErlAtom.atom("undefined")
                : ErlBinary.binary(element);
        Shape listMember = model.expectShape(listShape.getMember().getTarget());
        if (listMember instanceof StructureShape nested) {
            ErlFun decodeFun = ErlFun.fun(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("Item")),
                    buildDecodeStructureExpr(model, nested, "Item", sp)));
            return ErlCallLocal.callLocal(
                    "xml_child_struct_list",
                    ErlVar.var(xmlVar),
                    listNameExpr,
                    ErlBinary.binary(itemElement),
                    decodeFun);
        }
        return ErlCallLocal.callLocal(
                "xml_child_list", ErlVar.var(xmlVar), listNameExpr, ErlBinary.binary(itemElement));
    }

    private static List<ErlExpr> buildMembersFromXmlExprs(
            Model model,
            Iterable<MemberShape> members,
            String xmlVar,
            SymbolProvider sp) {
        List<ErlExpr> exprs = new ArrayList<>();
        for (MemberShape member : members) {
            exprs.add(buildMemberFromXmlExpr(model, member, xmlVar, sp));
        }
        return exprs;
    }

    private static ErlMatch buildMemberFromXmlExpr(
            Model model,
            MemberShape member,
            String xmlVar,
            SymbolProvider sp) {
        String field = BeamNameUtils.toSnakeCase(member.getMemberName());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(field);
        Shape target = model.expectShape(member.getTarget());
        if (BeamXmlBindingIndex.isXmlAttribute(member)) {
            return ErlMatch.match(
                    ErlVarPattern.varPattern(bindingVar),
                    ErlCase.caseExpr(
                            ErlVar.var(xmlVar),
                            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                            ErlClause.clause(
                                    List.of(ErlVarPattern.varPattern("_")),
                                    ErlCallLocal.callLocal(
                                            "xml_attribute",
                                            ErlVar.var(xmlVar),
                                            ErlBinary.binary(BeamXmlBindingIndex.memberElementName(member))))));
        }
        if (target instanceof ListShape listShape) {
            return ErlMatch.match(
                    ErlVarPattern.varPattern(bindingVar),
                    ErlCase.caseExpr(
                            ErlVar.var(xmlVar),
                            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                            ErlClause.clause(
                                    List.of(ErlVarPattern.varPattern("_")),
                                    buildDecodeListFieldExpr(model, member, listShape, xmlVar, sp))));
        }
        if (target instanceof StructureShape nested) {
            String element = BeamXmlBindingIndex.memberElementName(member);
            String nestedVar = bindingVar + "_xml";
            return ErlMatch.match(
                    ErlVarPattern.varPattern(bindingVar),
                    ErlCase.caseExpr(
                            ErlVar.var(xmlVar),
                            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                            ErlClause.clause(
                                    List.of(ErlVarPattern.varPattern("_")),
                                    ErlCase.caseExpr(
                                            ErlCallLocal.callLocal(
                                                    "find_element",
                                                    ErlBinary.binary(element),
                                                    ErlCallLocal.callLocal("element_content", ErlVar.var(xmlVar))),
                                            ErlClause.clause(
                                                    List.of(ErlAtomPattern.atomPattern("undefined")),
                                                    ErlAtom.atom("undefined")),
                                            ErlClause.clause(
                                                    List.of(ErlVarPattern.varPattern(nestedVar)),
                                                    buildDecodeStructureExpr(model, nested, nestedVar, sp))))));
        }
        return ErlMatch.match(
                ErlVarPattern.varPattern(bindingVar),
                ErlCase.caseExpr(
                        ErlVar.var(xmlVar),
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("_")),
                                ErlCallLocal.callLocal(
                                        "xml_child_text",
                                        ErlVar.var(xmlVar),
                                        ErlBinary.binary(BeamXmlBindingIndex.memberElementName(member))))));
    }

    private static List<ErlExpr> buildRequestBodyExprs(
            Model model,
            List<HttpBinding> payloadMembers,
            String method,
            SymbolProvider sp) {
        List<ErlExpr> exprs = new ArrayList<>();
        if (payloadMembers.isEmpty()
                || method.equals("GET")
                || method.equals("DELETE")
                || method.equals("HEAD")) {
            exprs.add(ErlMatch.match(ErlVarPattern.varPattern("Body"), ErlBinary.binary("")));
            return exprs;
        }

        HttpBinding payload = payloadMembers.get(0);
        MemberShape member = payload.getMember();
        Shape target = model.expectShape(member.getTarget());
        String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);

        if (target instanceof BlobShape || target instanceof StringShape) {
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Body"),
                    ErlCase.caseExpr(
                            ErlVar.var(bindingVar),
                            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")),
                            ErlClause.clause(List.of(ErlVarPattern.varPattern("Value")), ErlVar.var("Value")))));
            return exprs;
        }

        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        ErlExpr payloadValueExpr;
        if (target instanceof StructureShape structure) {
            String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
            payloadValueExpr = ErlExprBlock.block(
                    ErlMatch.match(
                            ErlVarPattern.varPattern("MemberMap"),
                            buildStructureXmlMapExpr(model, structure, "PayloadValue", recordTag)),
                    ErlCallLocal.callLocal(
                            "encode_xml",
                            ErlMap.map(ErlMapEntry.entry(
                                    ErlBinary.binary(rootElement),
                                    ErlVar.var("MemberMap"))),
                            ErlVar.var("XmlNs")));
        } else if (target instanceof UnionShape union) {
            payloadValueExpr = buildUnionPayloadEncodeExpr(model, union, rootElement, "PayloadValue", sp);
        } else {
            payloadValueExpr = ErlCallLocal.callLocal(
                    "encode_xml",
                    ErlMap.map(ErlMapEntry.entry(
                            ErlBinary.binary(rootElement),
                            ErlVar.var("PayloadValue"))),
                    ErlVar.var("XmlNs"));
        }

        exprs.add(ErlMatch.match(ErlVarPattern.varPattern("XmlNs"), ErlCallLocal.callLocal("xml_namespace")));
        exprs.add(ErlMatch.match(
                ErlVarPattern.varPattern("Body"),
                ErlCase.caseExpr(
                        ErlVar.var(bindingVar),
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("PayloadValue")),
                                payloadValueExpr))));
        return exprs;
    }

    private static List<ErlExpr> buildResponseBodyFromPayloadExprs(
            Model model,
            HttpBinding payload,
            SymbolProvider sp) {
        MemberShape member = payload.getMember();
        Shape target = model.expectShape(member.getTarget());
        String bindingVar = ErlangRestXmlSupport.toBindingVar(BeamNameUtils.toSnakeCase(member.getMemberName()));

        if (target instanceof BlobShape || target instanceof StringShape) {
            return List.of(ErlMatch.match(
                    ErlVarPattern.varPattern("Body"),
                    ErlCase.caseExpr(
                            ErlVar.var(bindingVar),
                            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")),
                            ErlClause.clause(List.of(ErlVarPattern.varPattern("Value")), ErlVar.var("Value")))));
        }

        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        ErlExpr payloadValueExpr;
        if (target instanceof StructureShape structure) {
            String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
            payloadValueExpr = ErlCallLocal.callLocal(
                    "encode_xml",
                    ErlMap.map(ErlMapEntry.entry(
                            ErlBinary.binary(rootElement),
                            buildStructureXmlMapExpr(model, structure, "PayloadValue", recordTag))),
                    ErlVar.var("XmlNs"));
        } else if (target instanceof UnionShape union) {
            payloadValueExpr = buildUnionPayloadEncodeExpr(model, union, rootElement, "PayloadValue", sp);
        } else {
            payloadValueExpr = ErlCallLocal.callLocal(
                    "encode_xml",
                    ErlMap.map(ErlMapEntry.entry(
                            ErlBinary.binary(rootElement),
                            ErlVar.var("PayloadValue"))),
                    ErlVar.var("XmlNs"));
        }

        return List.of(
                ErlMatch.match(ErlVarPattern.varPattern("XmlNs"), ErlCallLocal.callLocal("xml_namespace")),
                ErlMatch.match(
                        ErlVarPattern.varPattern("Body"),
                        ErlCase.caseExpr(
                                ErlVar.var(bindingVar),
                                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("PayloadValue")),
                                        payloadValueExpr))));
    }

    private static ErlExpr buildUnionPayloadEncodeExpr(
            Model model,
            UnionShape union,
            String rootElement,
            String valueVar,
            SymbolProvider sp) {
        List<ErlClause> clauses = new ArrayList<>();
        for (MemberShape member : union.members()) {
            String element = BeamXmlBindingIndex.memberElementName(member);
            String tag = unionTagForMember(sp, member);
            Shape memberTarget = model.expectShape(member.getTarget());
            ErlExpr innerValue;
            if (memberTarget instanceof StructureShape structure) {
                String recordTag = ErlangRestXmlSupport.recordName(sp.toSymbol(structure));
                innerValue = buildStructureXmlMapExpr(model, structure, "V", recordTag);
            } else {
                innerValue = ErlVar.var("V");
            }
            clauses.add(ErlClause.clause(
                    List.of(ErlTuplePattern.tuplePattern(
                            ErlAtomPattern.atomPattern(tag),
                            ErlVarPattern.varPattern("V"))),
                    ErlCallLocal.callLocal(
                            "encode_xml",
                            ErlMap.map(ErlMapEntry.entry(
                                    ErlBinary.binary(rootElement),
                                    ErlMap.map(ErlMapEntry.entry(
                                            ErlBinary.binary(element),
                                            innerValue)))),
                            ErlVar.var("XmlNs"))));
        }
        clauses.add(ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")));
        return ErlCase.caseExpr(ErlVar.var(valueVar), clauses.toArray(ErlClause[]::new));
    }

    private static ErlExpr buildStructureXmlMapExpr(
            Model model,
            StructureShape structure,
            String recordVar,
            String recordTag) {
        List<ErlMapEntry> entries = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            if (BeamXmlBindingIndex.isXmlAttribute(member)) {
                continue;
            }
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            String wireName = BeamXmlBindingIndex.memberElementName(member);
            entries.add(ErlMapEntry.entry(
                    ErlBinary.binary(wireName),
                    ErlRecordAccess.recordAccess(ErlVar.var(recordVar), recordTag, field)));
        }
        if (entries.isEmpty()) {
            return ErlMap.map();
        }
        return ErlMap.map(entries.toArray(ErlMapEntry[]::new));
    }

    private static ErlExpr flattenBindingCasesExpr(
            Model model,
            SymbolProvider sp,
            List<HttpBinding> bindings,
            boolean queryValues) {
        List<ErlExpr> cases = new ArrayList<>();
        for (HttpBinding binding : bindings) {
            String fieldVar = ErlangRestXmlSupport.toBindingVar(
                    BeamNameUtils.toSnakeCase(binding.getMember().getMemberName()));
            String valueVar = fieldVar + "Val";
            ErlExpr encodedValue = encodeBindingWireValueExpr(model, sp, binding.getMember(), valueVar, queryValues);
            cases.add(ErlCase.caseExpr(
                    ErlVar.var(fieldVar),
                    ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlList.list()),
                    ErlClause.clause(
                            List.of(ErlVarPattern.varPattern(valueVar)),
                            ErlList.list(ErlTuple.tuple(
                                    ErlBinary.binary(binding.getLocationName()),
                                    encodedValue)))));
        }
        return ErlCall.call("lists", "flatten", ErlList.list(cases.toArray(ErlExpr[]::new)));
    }

    private static ErlExpr encodeBindingWireValueExpr(
            Model model,
            SymbolProvider sp,
            MemberShape member,
            String valueVar,
            boolean queryValues) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof EnumShape || target instanceof IntEnumShape) {
            String helperName = sp.toSymbol(target).getName().replace("()", "");
            return ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var(valueVar));
        }
        if (queryValues) {
            return ErlCallLocal.callLocal("encode_query_value", ErlVar.var(valueVar));
        }
        return ErlCallLocal.callLocal("to_binary", ErlVar.var(valueVar));
    }

    private static List<ErlExpr> buildIdempotencyTokenExprs(StructureShape input, String inputRecord) {
        List<MemberShape> idempotencyMembers = input.members().stream()
                .filter(m -> m.hasTrait(IdempotencyTokenTrait.class))
                .toList();
        if (idempotencyMembers.isEmpty()) {
            return List.of();
        }
        List<ErlExpr> exprs = new ArrayList<>();
        String currentInput = "Input";
        int step = 1;
        for (MemberShape member : idempotencyMembers) {
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            String nextInput = "Input" + step;
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern(nextInput),
                    ErlCase.caseExpr(
                            ErlRecordAccess.recordAccess(ErlVar.var(currentInput), inputRecord, field),
                            ErlClause.clause(
                                    List.of(ErlAtomPattern.atomPattern("undefined")),
                                    ErlRecord.recordUpdate(
                                            ErlVar.var(currentInput),
                                            inputRecord,
                                            ErlRecordField.field(
                                                    field, ErlCallLocal.callLocal("generate_uuid")))),
                            ErlClause.clause(
                                    List.of(ErlVarPattern.varPattern("_")),
                                    ErlVar.var(currentInput)))));
            currentInput = nextInput;
            step++;
        }
        for (MemberShape member : idempotencyMembers) {
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern(ErlangRestXmlSupport.toBindingVar(field)),
                    ErlRecordAccess.recordAccess(ErlVar.var(currentInput), inputRecord, field)));
        }
        return exprs;
    }

    private static ErlExpr buildPathExpression(String uriTemplate, List<HttpBinding> labels) {
        if (labels.isEmpty()) {
            return ErlBinaryTemplate.binaryTemplate(ErlBinaryText.text(uriTemplate));
        }
        List<ErlBinarySegment> segments = new ArrayList<>();
        int pos = 0;
        while (pos < uriTemplate.length()) {
            int start = uriTemplate.indexOf('{', pos);
            if (start < 0) {
                segments.add(ErlBinaryText.text(uriTemplate.substring(pos)));
                break;
            }
            if (start > pos) {
                segments.add(ErlBinaryText.text(uriTemplate.substring(pos, start)));
            }
            int end = uriTemplate.indexOf('}', start);
            String labelName = uriTemplate.substring(start + 1, end);
            String fieldName = BeamNameUtils.toSnakeCase(labelName);
            segments.add(ErlBinaryExpr.expr(
                    ErlCallLocal.callLocal(
                            "uri_encode",
                            ErlCallLocal.callLocal("to_binary", ErlVar.var(ErlangRestXmlSupport.toBindingVar(fieldName)))),
                    true));
            pos = end + 1;
        }
        return ErlBinaryTemplate.binaryTemplate(segments.toArray(ErlBinarySegment[]::new));
    }

    private static List<ErlExpr> buildQueryParamsExprs(List<HttpBinding> queryParams) {
        if (queryParams.isEmpty()) {
            return List.of();
        }
        List<ErlExpr> exprs = new ArrayList<>();
        for (HttpBinding qp : queryParams) {
            String fieldName = BeamNameUtils.toSnakeCase(qp.getMember().getMemberName());
            String bindingVar = ErlangRestXmlSupport.toBindingVar(fieldName);
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("QueryExtra"),
                    ErlCase.caseExpr(
                            ErlVar.var(bindingVar),
                            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlList.list()),
                            ErlClause.clause(
                                    List.of(ErlVarPattern.varPattern("M")),
                                    List.of(ErlGuard.guard("is_map", ErlVar.var("M"))),
                                    ErlListComprehension.comprehension(
                                            ErlTuple.tuple(ErlVar.var("K"), ErlVar.var("V")),
                                            ErlTuplePattern.tuplePattern(
                                                    ErlVarPattern.varPattern("K"),
                                                    ErlVarPattern.varPattern("V")),
                                            ErlCall.call("maps", "to_list", ErlVar.var("M")))))));
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Query"),
                    ErlOp.op("++", ErlVar.var("Query"), ErlVar.var("QueryExtra"))));
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
            Optional<String> mediaType = member.getTrait(MediaTypeTrait.class)
                    .map(MediaTypeTrait::getValue);
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

    private static ErlRecordPattern encodeResponsePattern(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            StructureShape output,
            String outputRecord) {
        List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> respPrefixHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        if (respPayload.isEmpty() && !output.members().isEmpty()) {
            for (MemberShape member : output.members()) {
                addBindingField(fields, member.getMemberName());
            }
        } else {
            for (HttpBinding binding : ErlangRestXmlSupport.concat(respHeaders, respPrefixHeaders, respPayload)) {
                addBindingField(fields, binding.getMember().getMemberName());
            }
        }
        return new ErlRecordPattern(outputRecord, fields);
    }

    private static void addBindingField(List<ErlRecordFieldPattern> fields, String memberName) {
        String field = BeamNameUtils.toSnakeCase(memberName);
        fields.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(
                ErlangRestXmlSupport.toBindingVar(field))));
    }

    private static ErlRecordPattern recordBindingHead(String alias, String recordName, List<HttpBinding> bindings) {
        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (HttpBinding binding : bindings) {
            addBindingField(fields, binding.getMember().getMemberName());
        }
        return new ErlRecordPattern(recordName, fields, alias);
    }

    private static ErlRecordPattern httpRequestPattern() {
        return ErlRecordPattern.recordPattern(
                "http_request",
                ErlRecordFieldPattern.fieldPattern("query", ErlVarPattern.varPattern("Query")),
                ErlRecordFieldPattern.fieldPattern("headers", ErlVarPattern.varPattern("Headers")),
                ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));
    }
}
