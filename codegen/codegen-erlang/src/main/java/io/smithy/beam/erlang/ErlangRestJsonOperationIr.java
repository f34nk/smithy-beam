package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamRequestCompressionIndex;
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
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ErlangRestJsonOperationIr {
    private ErlangRestJsonOperationIr() {}

    static ErlFunction buildEncodeRequest(
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
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> reqPayload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

        List<HttpBinding> patternBindings =
                concat(labels, queries, queryParams, headers, prefixHeaders, docMembers, reqPayload);
        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "encode_" + opName + "_request",
                encodeWithConfig ? "client_config(), " + inputType : inputType,
                "#http_request{}");
        List<ErlPattern> patterns = encodeWithConfig
                ? List.of(
                        ErlVarPattern.varPattern("Config"),
                        recordBindingHead("Input", inputRecord, patternBindings))
                : List.of(recordBindingHead("Input", inputRecord, patternBindings));

        List<ErlExpr> body = new ArrayList<>();
        body.addAll(buildIdempotencyTokenExprs(input, inputRecord));
        body.add(ErlMatch.match(ErlVarPattern.varPattern("Path"), buildPathExpression(uriTemplate, labels)));
        body.add(ErlMatch.match(ErlVarPattern.varPattern("Query"), buildQueryListExpr(model, queries)));
        body.addAll(buildQueryParamsExprs(queryParams));
        body.addAll(buildRequestHeadersExprs(model, op, headers, prefixHeaders, sp));
        body.addAll(buildRequestBodyExprs(model, httpIndex, reqPayload, docMembers, method, sp, eventStreamModule));
        body.addAll(captureOptionalExprs(writer ->
                ErlangHttpChecksumEmitter.emitRequestChecksumHeaders(writer, model, op, sp, "Headers")));
        body.addAll(captureOptionalExprs(writer -> emitRequestCompression(writer, op)));

        boolean streamingRequestPayload = hasStreamingRequestPayload(model, reqPayload, method);
        if (streamingRequestPayload) {
            HttpBinding payload = reqPayload.get(0);
            String fieldName = BeamNameUtils.toSnakeCase(payload.getMember().getMemberName());
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Stream"),
                    ErlCase.caseExpr(
                            ErlVar.var(toBindingVar(fieldName)),
                            ErlClause.clause(
                                    List.of(ErlAtomPattern.atomPattern("undefined")),
                                    ErlAtom.atom("undefined")),
                            ErlClause.clause(
                                    List.of(ErlVarPattern.varPattern("Value")),
                                    ErlVar.var("Value")))));
        }

        BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
        boolean hasHostLabels = !hostLabelIndex.hostLabelMembers(op).isEmpty()
                && op.hasTrait(EndpointTrait.class);
        if (hasHostLabels) {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Host"),
                    ErlCallLocal.callLocal("build_host", ErlVar.var("Input"), ErlVar.var("Config"))));
        }

        String requestHeaders = headersWithChecksum(body);
        body.add(buildHttpRequestRecord(
                method, requestHeaders, streamingRequestPayload, hasHostLabels));

        return ErlFunction.functionWithDocAndSpec(
                "encode_" + opName + "_request",
                patterns.size(),
                ErlFunctionDoc.functionDoc("Encode HTTP request for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(patterns, ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
    }

    static ErlFunction buildDecodeRequest(
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
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> reqPayload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
        boolean streamingRequestPayload = hasStreamingRequestPayload(model, reqPayload, null);

        ErlFunctionSpec spec = labels.isEmpty()
                ? ErlFunctionSpec.functionSpec(
                        "decode_" + opName + "_request", "#http_request{}", inputType)
                : ErlFunctionSpec.functionSpec(
                        "decode_" + opName + "_request", "#http_request{}, map()", inputType);

        List<ErlPattern> patterns = new ArrayList<>();
        patterns.add(httpRequestPattern(streamingRequestPayload));
        if (!labels.isEmpty()) {
            patterns.add(ErlVarPattern.varPattern("LabelMap"));
        }

        List<ErlExpr> body = new ArrayList<>();
        if (!docMembers.isEmpty()) {
            body.add(ErlMatch.match(ErlVarPattern.varPattern("Decoded"), decodeBodyJsonExpr()));
        }
        body.add(buildInputRecord(inputRecord, model, httpIndex, sp, eventStreamModule, labels, queries, queryParams,
                headers, prefixHeaders, docMembers, reqPayload, streamingRequestPayload));

        return ErlFunction.functionWithDocAndSpec(
                "decode_" + opName + "_request",
                patterns.size(),
                ErlFunctionDoc.functionDoc("Decode HTTP request for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(patterns, ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
    }

    static ErlFunction buildDecodeResponse(
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
        List<HttpBinding> respPrefixHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
        List<HttpBinding> respCode = httpIndex.getResponseBindings(op, HttpBinding.Location.RESPONSE_CODE);
        boolean streamingResponsePayload = !respPayload.isEmpty()
                && isStreamingBlob(model, respPayload.get(0).getMember());
        boolean eventStreamResponsePayload = !respPayload.isEmpty()
                && BeamEventStreamIndex.of(model).isEventStreamMember(respPayload.get(0).getMember());
        String eventStreamModule = layout.eventStreamModuleName();

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "decode_" + opName + "_response",
                "#http_response{}",
                "{'ok', " + outputType + "} | {'error', term()}");

        List<ErlGuard> successGuards = new ArrayList<>();
        List<ErlRecordFieldPattern> successFields = new ArrayList<>();
        successFields.add(ErlRecordFieldPattern.fieldPattern("status", ErlVarPattern.varPattern("HttpStatus")));
        successFields.add(ErlRecordFieldPattern.fieldPattern("headers", ErlVarPattern.varPattern("Headers")));
        successFields.add(ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));
        if (streamingResponsePayload) {
            successFields.add(ErlRecordFieldPattern.fieldPattern("stream", ErlVarPattern.varPattern("Stream")));
        }
        if (!respCode.isEmpty()) {
            successGuards.add(ErlGuard.exprGuard(
                    ErlOp.op(">=", ErlVar.var("HttpStatus"), ErlInteger.integer(200))));
            successGuards.add(ErlGuard.exprGuard(
                    ErlOp.op("<", ErlVar.var("HttpStatus"), ErlInteger.integer(300))));
        }

        ErlRecordPattern successPattern;
        if (!respCode.isEmpty()) {
            successPattern = ErlRecordPattern.recordPattern("http_response", successFields.toArray(ErlRecordFieldPattern[]::new));
        } else {
            successFields.set(0, ErlRecordFieldPattern.fieldPattern("status", ErlIntegerPattern.integerPattern(successCode)));
            successPattern = ErlRecordPattern.recordPattern("http_response", successFields.toArray(ErlRecordFieldPattern[]::new));
        }

        List<ErlExpr> successBody = new ArrayList<>();
        boolean needsContentTypeCheck = responsePayloadRequiresContentTypeCheck(model, respPayload);
        if (needsContentTypeCheck) {
            String expectedContentType = resolvedResponseContentType(model, op);
            List<ErlClause> contentTypeClauses = new ArrayList<>();
            contentTypeClauses.add(ErlClause.clause(
                    List.of(ErlAtomPattern.atomPattern("false")),
                    ErlTuple.tuple(
                            ErlAtom.atom("error"),
                            ErlTuple.tuple(
                                    ErlAtom.atom("invalid_content_type"),
                                    ErlCall.call(
                                            "proplists",
                                            "get_value",
                                            ErlBinary.binary("Content-Type"),
                                            ErlVar.var("Headers"),
                                            ErlAtom.atom("undefined"))))));
            contentTypeClauses.add(ErlClause.clause(
                    List.of(ErlAtomPattern.atomPattern("true")),
                    buildDecodeResponseSuccessBody(
                            model, op, httpIndex, sp, outputRecord, respHeaders, respPrefixHeaders, respDoc,
                            respPayload, respCode, streamingResponsePayload, eventStreamResponsePayload,
                            eventStreamModule, needsContentTypeCheck)));
            successBody.add(ErlCase.caseExpr(
                    ErlCallLocal.callLocal(
                            "content_type_matches",
                            ErlVar.var("Headers"),
                            ErlBinary.binary(expectedContentType)),
                    contentTypeClauses.toArray(ErlClause[]::new)));
        } else {
            successBody.add(buildDecodeResponseSuccessBody(
                    model, op, httpIndex, sp, outputRecord, respHeaders, respPrefixHeaders, respDoc, respPayload,
                    respCode, streamingResponsePayload, eventStreamResponsePayload, eventStreamModule,
                    needsContentTypeCheck));
        }

        ErlClause successClause = successGuards.isEmpty()
                ? ErlClause.clause(List.of(successPattern), successBody.toArray(ErlExpr[]::new))
                : ErlClause.clause(List.of(successPattern), successGuards, successBody.toArray(ErlExpr[]::new));

        ErlClause errorClause = ErlClause.clause(
                List.of(httpResponseErrorPattern()),
                ErlCallLocal.callLocal(
                        "decode_" + opName + "_response_error",
                        ErlVar.var("Status"),
                        ErlVar.var("RespHeaders"),
                        ErlVar.var("Body")));

        return ErlFunction.functionWithDocAndSpec(
                "decode_" + opName + "_response",
                1,
                ErlFunctionDoc.functionDoc("Decode HTTP response for " + op.getId() + "."),
                spec,
                List.of(successClause, errorClause));
    }

    static ErlFunction buildErrorDispatch(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        String opName = sp.toSymbol(op).getName();
        List<ShapeId> errors = new ArrayList<>(op.getErrors());

        List<ErlClause> clauses = new ArrayList<>();
        for (ShapeId errorId : errors) {
            StructureShape errShape = model.expectShape(errorId, StructureShape.class);
            int httpStatus = errShape.hasTrait(HttpErrorTrait.class)
                    ? errShape.expectTrait(HttpErrorTrait.class).getCode()
                    : -1;
            if (httpStatus <= 0) {
                continue;
            }
            String recName = recordName(sp.toSymbol(errShape));
            clauses.add(ErlClause.clause(
                    List.of(
                            ErlIntegerPattern.integerPattern(httpStatus),
                            ErlVarPattern.varPattern("_Hdrs"),
                            ErlVarPattern.varPattern("Body")),
                    ErlExprBlock.block(
                            ErlMatch.match(
                                    ErlVarPattern.varPattern("Decoded"),
                                    ErlCallLocal.callLocal("decode_json_body", ErlVar.var("Body"))),
                            ErlTuple.tuple(
                                    ErlAtom.atom("error"),
                                    buildErrorRecord(recName, model, errShape, sp)))));
        }

        boolean hasTypeDiscriminated = errors.stream().anyMatch(e ->
                !model.expectShape(e, StructureShape.class).hasTrait(HttpErrorTrait.class));

        if (hasTypeDiscriminated) {
            List<ErlClause> typeClauses = new ArrayList<>();
            for (ShapeId errorId : errors) {
                StructureShape errShape = model.expectShape(errorId, StructureShape.class);
                if (errShape.hasTrait(HttpErrorTrait.class)) {
                    continue;
                }
                String recName = recordName(sp.toSymbol(errShape));
                typeClauses.add(ErlClause.clause(
                        List.of(ErlBinaryPattern.binaryPattern(errorId.getName())),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                buildErrorRecord(recName, model, errShape, sp))));
            }
            typeClauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("_")),
                    ErlTuple.tuple(
                            ErlAtom.atom("error"),
                            ErlTuple.tuple(
                                    ErlAtom.atom("unknown_error"),
                                    ErlVar.var("Status"),
                                    ErlVar.var("Body")))));
            clauses.add(ErlClause.clause(
                    List.of(
                            ErlVarPattern.varPattern("Status"),
                            ErlVarPattern.varPattern("_Hdrs"),
                            ErlVarPattern.varPattern("Body")),
                    List.of(ErlGuard.exprGuard(ErlOp.op(">=", ErlVar.var("Status"), ErlInteger.integer(400)))),
                    ErlExprBlock.block(
                            ErlMatch.match(
                                    ErlVarPattern.varPattern("Decoded"),
                                    ErlCallLocal.callLocal("decode_json_body", ErlVar.var("Body"))),
                            ErlMatch.match(
                                    ErlVarPattern.varPattern("ErrorType"),
                                    ErlCall.call(
                                            "maps",
                                            "get",
                                            ErlBinary.binary("__type"),
                                            ErlVar.var("Decoded"),
                                            ErlAtom.atom("undefined"))),
                            ErlCase.caseExpr(ErlVar.var("ErrorType"), typeClauses.toArray(ErlClause[]::new)))));
        } else {
            clauses.add(ErlClause.clause(
                    List.of(
                            ErlVarPattern.varPattern("Status"),
                            ErlVarPattern.varPattern("_Hdrs"),
                            ErlVarPattern.varPattern("Body")),
                    ErlTuple.tuple(
                            ErlAtom.atom("error"),
                            ErlTuple.tuple(
                                    ErlAtom.atom("unknown_error"),
                                    ErlVar.var("Status"),
                                    ErlVar.var("Body")))));
        }

        return new ErlFunction(
                "decode_" + opName + "_response_error",
                3,
                ErlFunctionDoc.functionDoc("Error dispatch for " + op.getId() + "."),
                null,
                clauses);
    }

    static ErlFunction buildErrorDispatch(Model model, OperationShape op, SymbolProvider sp) {
        return buildErrorDispatch(model, null, op, HttpBindingIndex.of(model), sp);
    }

    static ErlFunction buildEncodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = recordName(sp.toSymbol(output));
        String outputType = sp.toSymbol(output).getName();

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "encode_" + opName + "_response", outputType, "#http_response{}");

        return ErlFunction.functionWithDocAndSpec(
                "encode_" + opName + "_response",
                1,
                ErlFunctionDoc.functionDoc("Encode HTTP response for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(
                        List.of(encodeResponsePattern(model, op, httpIndex, sp, output, outputRecord)),
                        captureBody(writer -> ErlangRestJson1Emitter.emitEncodeResponseBody(
                                writer, model, op, httpIndex, sp)))));
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
        List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (HttpBinding b : concat(respHeaders, respPrefixHeaders, respDoc, respPayload)) {
            String field = BeamNameUtils.toSnakeCase(b.getMember().getMemberName());
            fields.add(ErlRecordFieldPattern.fieldPattern(
                    field, ErlVarPattern.varPattern(toBindingVar(field))));
        }
        return new ErlRecordPattern(outputRecord, fields);
    }

    static ErlFunction buildErrorResponseEncoder(Model model, ShapeId errorId, SymbolProvider sp) {
        StructureShape errShape = model.expectShape(errorId, StructureShape.class);
        String recName = recordName(sp.toSymbol(errShape));
        int status = errShape.hasTrait(HttpErrorTrait.class)
                ? errShape.expectTrait(HttpErrorTrait.class).getCode()
                : 500;

        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (MemberShape m : errShape.members()) {
            if (m.getMemberName().equals("__beam_error_kind")) {
                continue;
            }
            String field = BeamNameUtils.toSnakeCase(m.getMemberName());
            fields.add(ErlRecordFieldPattern.fieldPattern(
                    field, ErlVarPattern.varPattern(toBindingVar(field))));
        }

        List<ErlMapEntry> bodyEntries = new ArrayList<>();
        bodyEntries.add(ErlMapEntry.entry(ErlBinary.binary("__type"), ErlBinary.binary(errorId.getName())));
        for (MemberShape m : errShape.members()) {
            if (m.getMemberName().equals("__beam_error_kind")) {
                continue;
            }
            String field = BeamNameUtils.toSnakeCase(m.getMemberName());
            bodyEntries.add(ErlMapEntry.entry(
                    ErlBinary.binary(m.getMemberName()), ErlVar.var(toBindingVar(field))));
        }

        ErlExpr body = ErlExprBlock.block(
                ErlMatch.match(ErlVarPattern.varPattern("BodyMap"), ErlMap.map(bodyEntries.toArray(ErlMapEntry[]::new))),
                ErlMatch.match(
                        ErlVarPattern.varPattern("Body"),
                        ErlCall.call("jsone", "encode", ErlVar.var("BodyMap"))),
                ErlRecord.record(
                        "http_response",
                        ErlRecordField.field("status", ErlInteger.integer(status)),
                        ErlRecordField.field(
                                "headers",
                                ErlList.list(ErlTuple.tuple(
                                        ErlBinary.binary("Content-Type"),
                                        ErlBinary.binary("application/json")))),
                        ErlRecordField.field("body", ErlVar.var("Body"))));

        return ErlFunction.functionWithDocAndSpec(
                "encode_" + recName + "_response",
                1,
                ErlFunctionDoc.functionDoc("Encode HTTP error response for " + errorId + "."),
                ErlFunctionSpec.functionSpec(
                        "encode_" + recName + "_response",
                        "#" + recName + "{}",
                        "#http_response{}"),
                List.of(ErlClause.clause(List.of(new ErlRecordPattern(recName, fields)), body)));
    }

    static ErlRecordPattern memberBindingHead(
            String alias, String recordName, StructureShape structure, SymbolProvider sp) {
        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            fields.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(toBindingVar(field))));
        }
        return new ErlRecordPattern(recordName, fields, alias);
    }

    static ErlRecordPattern outputBindingHead(String recordName, StructureShape structure, SymbolProvider sp) {
        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            fields.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(toBindingVar(field))));
        }
        return new ErlRecordPattern(recordName, fields);
    }

    static ErlRecord buildDocumentRecordFromDecoded(
            String recordName,
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            List<MemberShape> members,
            String eventStreamModule) {
        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : members) {
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof UnionShape union
                    && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
                String helper = ErlangEventStreamEmitter.helperName(sp, union);
                fields.add(ErlRecordField.field(
                        fieldName,
                        ErlCall.call(eventStreamModule, "decode_" + helper, ErlVar.var("Body"))));
            } else {
                ErlExpr raw = ErlangCodecHelperIr.mapsGetDefault(
                        ErlBinary.binary(jsonKey(member)), ErlVar.var("Decoded"), ErlAtom.atom("undefined"));
                fields.add(ErlRecordField.field(fieldName, decodeJsonExpr(model, sp, httpIndex, member, raw)));
            }
        }
        return ErlRecord.record(recordName, fields.toArray(ErlRecordField[]::new));
    }

    static List<ErlExpr> buildDocumentBodyEncodeExprs(
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
            return List.of(ErlMatch.match(
                    ErlVarPattern.varPattern("Body"),
                    ErlCallLocal.callLocal(
                            "iolist_to_binary",
                            ErlCall.call(
                                    eventStreamModule,
                                    "encode_" + helper,
                                    ErlVar.var(toBindingVar(fieldName))))));
        }
        List<ErlMapEntry> entries = new ArrayList<>();
        for (MemberShape member : members) {
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            entries.add(ErlMapEntry.entry(
                    ErlBinary.binary(jsonKey(member)),
                    encodeJsonExpr(model, sp, httpIndex, member, toBindingVar(fieldName))));
        }
        return List.of(
                ErlMatch.match(
                        ErlVarPattern.varPattern("BodyMap"),
                        ErlCall.call(
                                "maps",
                                "filter",
                                ErlFun.fun(ErlClause.clause(
                                        List.of(
                                                ErlVarPattern.varPattern("_"),
                                                ErlVarPattern.varPattern("V")),
                                        ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                                ErlMap.map(entries.toArray(ErlMapEntry[]::new)))),
                ErlMatch.match(
                        ErlVarPattern.varPattern("Body"),
                        ErlCall.call("jsone", "encode", ErlVar.var("BodyMap"))));
    }

    private static ErlExpr buildDecodeResponseSuccessBody(
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
        List<ErlExpr> body = new ArrayList<>();
        if (!respDoc.isEmpty()
                || (!respPayload.isEmpty() && !needsContentTypeCheck && !eventStreamResponsePayload)) {
            body.add(ErlMatch.match(ErlVarPattern.varPattern("Decoded"), decodeBodyJsonExpr()));
        }
        for (HttpBinding hb : respHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
            String bindingVar = toBindingVar(fieldName);
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern(bindingVar),
                    ErlCall.call(
                            "proplists",
                            "get_value",
                            ErlBinary.binary(hb.getLocationName()),
                            ErlVar.var("Headers"),
                            ErlAtom.atom("undefined"))));
        }

        List<ErlRecordField> recordFields = new ArrayList<>();
        for (HttpBinding hb : respHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
            recordFields.add(ErlRecordField.field(fieldName, ErlVar.var(toBindingVar(fieldName))));
        }
        for (HttpBinding ph : respPrefixHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
            recordFields.add(ErlRecordField.field(
                    fieldName,
                    ErlCallLocal.callLocal(
                            "prefix_headers_from_list",
                            ErlVar.var("Headers"),
                            ErlBinary.binary(ph.getLocationName()))));
        }
        for (HttpBinding db : respDoc) {
            String fieldName = BeamNameUtils.toSnakeCase(db.getMember().getMemberName());
            String wireKey = jsonKey(db.getMember());
            ErlExpr raw = ErlangCodecHelperIr.mapsGetDefault(
                    ErlBinary.binary(wireKey), ErlVar.var("Decoded"), ErlAtom.atom("undefined"));
            recordFields.add(ErlRecordField.field(fieldName, decodeJsonExpr(model, sp, httpIndex, db.getMember(), raw)));
        }
        for (HttpBinding pb : respPayload) {
            String fieldName = BeamNameUtils.toSnakeCase(pb.getMember().getMemberName());
            if (isStreamingBlob(model, pb.getMember())) {
                recordFields.add(ErlRecordField.field(fieldName, ErlVar.var("Stream")));
            } else if (BeamEventStreamIndex.of(model).isEventStreamMember(pb.getMember())) {
                UnionShape union = model.expectShape(pb.getMember().getTarget(), UnionShape.class);
                String helper = ErlangEventStreamEmitter.helperName(sp, union);
                recordFields.add(ErlRecordField.field(
                        fieldName,
                        ErlCall.call(
                                eventStreamModule,
                                "decode_" + helper,
                                ErlVar.var("Body"))));
            } else {
                recordFields.add(ErlRecordField.field(fieldName, ErlVar.var("Body")));
            }
        }
        for (HttpBinding rcb : respCode) {
            String fieldName = BeamNameUtils.toSnakeCase(rcb.getMember().getMemberName());
            recordFields.add(ErlRecordField.field(fieldName, ErlVar.var("HttpStatus")));
        }

        ErlTuple success = ErlTuple.tuple(
                ErlAtom.atom("ok"),
                ErlRecord.record(outputRecord, recordFields.toArray(ErlRecordField[]::new)));
        if (BeamHttpChecksumIndex.of(model).responseChecksums(op).isEmpty()) {
            body.add(success);
        } else {
            body.addAll(captureOptionalExprs(writer ->
                    ErlangHttpChecksumEmitter.emitResponseChecksumGuard(writer, model, op, success.asString())));
        }
        return body.size() == 1 ? body.get(0) : ErlExprBlock.block(body.toArray(ErlExpr[]::new));
    }

    private static ErlRecord buildInputRecord(
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
        List<ErlRecordField> fields = new ArrayList<>();
        for (HttpBinding lb : labels) {
            String fieldName = BeamNameUtils.toSnakeCase(lb.getMember().getMemberName());
            fields.add(ErlRecordField.field(
                    fieldName,
                    ErlCallLocal.callLocal(
                            "uri_decode",
                            ErlCall.call(
                                    "maps",
                                    "get",
                                    ErlBinary.binary(lb.getMember().getMemberName()),
                                    ErlVar.var("LabelMap"),
                                    ErlAtom.atom("undefined")))));
        }
        for (HttpBinding qb : queries) {
            String fieldName = BeamNameUtils.toSnakeCase(qb.getMember().getMemberName());
            fields.add(ErlRecordField.field(
                    fieldName,
                    ErlCallLocal.callLocal(
                            "decode_query_param",
                            ErlCall.call(
                                    "maps",
                                    "get",
                                    ErlBinary.binary(qb.getLocationName()),
                                    ErlVar.var("Query"),
                                    ErlAtom.atom("undefined")))));
        }
        for (HttpBinding qp : queryParams) {
            String fieldName = BeamNameUtils.toSnakeCase(qp.getMember().getMemberName());
            fields.add(ErlRecordField.field(
                    fieldName,
                    ErlCall.call("maps", "from_list", ErlCall.call("maps", "to_list", ErlVar.var("Query")))));
        }
        for (HttpBinding hb : headers) {
            String fieldName = BeamNameUtils.toSnakeCase(hb.getMember().getMemberName());
            fields.add(ErlRecordField.field(
                    fieldName,
                    ErlCall.call(
                            "proplists",
                            "get_value",
                            ErlBinary.binary(hb.getLocationName()),
                            ErlVar.var("Headers"),
                            ErlAtom.atom("undefined"))));
        }
        for (HttpBinding ph : prefixHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
            fields.add(ErlRecordField.field(
                    fieldName,
                    ErlCallLocal.callLocal(
                            "prefix_headers_from_list",
                            ErlVar.var("Headers"),
                            ErlBinary.binary(ph.getLocationName()))));
        }
        for (HttpBinding db : docMembers) {
            String fieldName = BeamNameUtils.toSnakeCase(db.getMember().getMemberName());
            String wireKey = jsonKey(db.getMember());
            ErlExpr raw = ErlangCodecHelperIr.mapsGetDefault(
                    ErlBinary.binary(wireKey), ErlVar.var("Decoded"), ErlAtom.atom("undefined"));
            fields.add(ErlRecordField.field(fieldName, decodeJsonExpr(model, sp, httpIndex, db.getMember(), raw)));
        }
        for (HttpBinding pb : reqPayload) {
            String fieldName = BeamNameUtils.toSnakeCase(pb.getMember().getMemberName());
            if (isStreamingBlob(model, pb.getMember())) {
                fields.add(ErlRecordField.field(fieldName, ErlVar.var("Stream")));
            } else if (BeamEventStreamIndex.of(model).isEventStreamMember(pb.getMember())) {
                UnionShape union = model.expectShape(pb.getMember().getTarget(), UnionShape.class);
                String helper = ErlangEventStreamEmitter.helperName(sp, union);
                fields.add(ErlRecordField.field(
                        fieldName,
                        ErlCall.call(
                                eventStreamModule,
                                "decode_" + helper,
                                ErlVar.var("Body"))));
            } else {
                fields.add(ErlRecordField.field(fieldName, ErlVar.var("Body")));
            }
        }
        return ErlRecord.record(inputRecord, fields.toArray(ErlRecordField[]::new));
    }

    static ErlRecord buildErrorRecord(
            String recName, Model model, StructureShape errShape, SymbolProvider sp) {
        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : errShape.members()) {
            if (member.getMemberName().equals("__beam_error_kind")) {
                continue;
            }
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            fields.add(ErlRecordField.field(
                    field,
                    ErlCall.call(
                            "maps",
                            "get",
                            ErlBinary.binary(member.getMemberName()),
                            ErlVar.var("Decoded"),
                            ErlAtom.atom("undefined"))));
        }
        return ErlRecord.record(recName, fields.toArray(ErlRecordField[]::new));
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
                    ErlVarPattern.varPattern(toBindingVar(field)),
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
                            ErlCallLocal.callLocal("to_binary", ErlVar.var(toBindingVar(fieldName)))),
                    true));
            pos = end + 1;
        }
        return ErlBinaryTemplate.binaryTemplate(segments.toArray(ErlBinarySegment[]::new));
    }

    private static ErlExpr buildQueryListExpr(Model model, List<HttpBinding> queries) {
        if (queries.isEmpty()) {
            return ErlList.list();
        }
        List<ErlExpr> parts = new ArrayList<>();
        for (HttpBinding qb : queries) {
            String bindingVar = toBindingVar(BeamNameUtils.toSnakeCase(qb.getMember().getMemberName()));
            Shape target = model.expectShape(qb.getMember().getTarget());
            ErlFun fun = queryFiltermapFun(qb.getLocationName());
            ErlExpr listArg = target instanceof ListShape
                    ? ErlCase.caseExpr(
                            ErlVar.var(bindingVar),
                            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlList.list()),
                            ErlClause.clause(List.of(ErlVarPattern.varPattern("V")), ErlVar.var("V")))
                    : ErlList.list(ErlVar.var(bindingVar));
            parts.add(ErlCall.filtermap(fun, listArg));
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        ErlExpr combined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            combined = ErlOp.op("++", combined, parts.get(i));
        }
        return combined;
    }

    private static ErlFun queryFiltermapFun(String paramName) {
        return ErlFun.fun(
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("V")),
                        List.of(ErlGuard.exprGuard(ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                        ErlTuple.tuple(
                                ErlAtom.atom("true"),
                                ErlTuple.tuple(
                                        ErlBinary.binary(paramName),
                                        ErlCallLocal.callLocal("encode_query_value", ErlVar.var("V"))))),
                ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), ErlAtom.atom("false")));
    }

    private static List<ErlExpr> buildQueryParamsExprs(List<HttpBinding> queryParams) {
        if (queryParams.isEmpty()) {
            return List.of();
        }
        List<ErlExpr> exprs = new ArrayList<>();
        for (HttpBinding qp : queryParams) {
            String fieldName = BeamNameUtils.toSnakeCase(qp.getMember().getMemberName());
            String bindingVar = toBindingVar(fieldName);
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

    private static List<ErlExpr> buildRequestHeadersExprs(
            Model model,
            OperationShape op,
            List<HttpBinding> headers,
            List<HttpBinding> prefixHeaders,
            SymbolProvider sp) {
        String requestContentType = resolvedRequestContentType(model, op);
        List<ErlExpr> exprs = new ArrayList<>();
        if (headers.isEmpty()) {
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlList.list(ErlTuple.tuple(
                            ErlBinary.binary("Content-Type"),
                            ErlBinary.binary(requestContentType)))));
        } else {
            List<ErlClause> headerClauses = new ArrayList<>();
            for (HttpBinding hb : headers) {
                headerClauses.add(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("V")),
                        List.of(ErlGuard.exprGuard(ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                        ErlTuple.tuple(
                                ErlAtom.atom("true"),
                                ErlTuple.tuple(
                                        ErlBinary.binary(hb.getLocationName()),
                                        ErlCallLocal.callLocal("to_binary", ErlVar.var("V"))))));
            }
            headerClauses.add(ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), ErlAtom.atom("false")));
            List<ErlExpr> headerArgs = headers.stream()
                    .map(hb -> (ErlExpr) ErlVar.var(toBindingVar(BeamNameUtils.toSnakeCase(hb.getMember().getMemberName()))))
                    .toList();
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers0"),
                    ErlCall.filtermap(
                            ErlFun.fun(headerClauses.toArray(ErlClause[]::new)),
                            ErlList.list(headerArgs.toArray(ErlExpr[]::new)))));
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlList.cons(
                            ErlTuple.tuple(
                                    ErlBinary.binary("Content-Type"),
                                    ErlBinary.binary(requestContentType)),
                            ErlVar.var("Headers0"))));
        }
        for (HttpBinding ph : prefixHeaders) {
            String fieldName = BeamNameUtils.toSnakeCase(ph.getMember().getMemberName());
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Headers"),
                    ErlOp.op(
                            "++",
                            ErlVar.var("Headers"),
                            ErlCallLocal.callLocal(
                                    "prefix_headers_to_list",
                                    ErlBinary.binary(ph.getLocationName()),
                                    ErlVar.var(toBindingVar(fieldName))))));
        }
        return exprs;
    }

    private static List<ErlExpr> buildRequestBodyExprs(
            Model model,
            HttpBindingIndex httpIndex,
            List<HttpBinding> reqPayload,
            List<HttpBinding> docMembers,
            String method,
            SymbolProvider sp,
            String eventStreamModule) {
        List<ErlExpr> exprs = new ArrayList<>();
        if (!reqPayload.isEmpty()
                && !method.equals("GET")
                && !method.equals("DELETE")
                && !method.equals("HEAD")) {
            HttpBinding payload = reqPayload.get(0);
            MemberShape member = payload.getMember();
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String bindingVar = toBindingVar(fieldName);
            if (isStreamingBlob(model, member)) {
                exprs.add(ErlMatch.match(ErlVarPattern.varPattern("Body"), ErlBinary.binary("")));
                return exprs;
            }
            if (BeamEventStreamIndex.of(model).isEventStreamMember(member)) {
                UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
                String helper = ErlangEventStreamEmitter.helperName(sp, union);
                exprs.add(ErlMatch.match(
                        ErlVarPattern.varPattern("Body"),
                        ErlCallLocal.callLocal(
                                "iolist_to_binary",
                                ErlCall.call(
                                        eventStreamModule,
                                        "encode_" + helper,
                                        ErlVar.var(bindingVar)))));
                return exprs;
            }
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof BlobShape || target instanceof StringShape) {
                exprs.add(ErlMatch.match(
                        ErlVarPattern.varPattern("Body"),
                        ErlCase.caseExpr(
                                ErlVar.var(bindingVar),
                                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")),
                                ErlClause.clause(List.of(ErlVarPattern.varPattern("Value")), ErlVar.var("Value")))));
                return exprs;
            }
        }

        if (docMembers.isEmpty() || method.equals("GET") || method.equals("DELETE") || method.equals("HEAD")) {
            exprs.add(ErlMatch.match(ErlVarPattern.varPattern("Body"), ErlBinary.binary("")));
        } else {
            List<ErlMapEntry> entries = new ArrayList<>();
            for (HttpBinding db : docMembers) {
                String fieldName = BeamNameUtils.toSnakeCase(db.getMember().getMemberName());
                entries.add(ErlMapEntry.entry(
                        ErlBinary.binary(jsonKey(db.getMember())),
                        encodeJsonExpr(model, sp, httpIndex, db.getMember(), toBindingVar(fieldName))));
            }
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("BodyMap"),
                    ErlCall.call(
                            "maps",
                            "filter",
                            ErlFun.fun(ErlClause.clause(
                                    List.of(
                                            ErlVarPattern.varPattern("_"),
                                            ErlVarPattern.varPattern("V")),
                                    ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                            ErlMap.map(entries.toArray(ErlMapEntry[]::new)))));
            exprs.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Body"),
                    ErlCall.call("jsone", "encode", ErlVar.var("BodyMap"))));
        }
        return exprs;
    }

    private static ErlRecord buildHttpRequestRecord(
            String method,
            String headersVar,
            boolean streamingRequestPayload,
            boolean hasHostLabels) {
        List<ErlRecordField> fields = new ArrayList<>();
        fields.add(ErlRecordField.field("method", ErlBinary.binary(method)));
        fields.add(ErlRecordField.field("path", ErlVar.var("Path")));
        fields.add(ErlRecordField.field(
                "query",
                ErlCall.call("maps", "from_list", ErlVar.var("Query"))));
        fields.add(ErlRecordField.field("headers", ErlVar.var(headersVar)));
        fields.add(ErlRecordField.field("body", ErlVar.var("Body")));
        if (streamingRequestPayload) {
            fields.add(ErlRecordField.field("stream", ErlVar.var("Stream")));
        }
        if (hasHostLabels) {
            fields.add(ErlRecordField.field("host", ErlVar.var("Host")));
        }
        return ErlRecord.record("http_request", fields.toArray(ErlRecordField[]::new));
    }

    static ErlCase decodeBodyJsonExpr() {
        return ErlCase.caseExpr(
                ErlVar.var("Body"),
                ErlClause.clause(List.of(ErlBinaryPattern.binaryPattern("")), ErlMap.map()),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_")),
                        ErlCase.caseExpr(
                                ErlCall.call("jsone", "try_decode", ErlVar.var("Body")),
                                ErlClause.clause(
                                        List.of(ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("ok"),
                                                ErlVarPattern.varPattern("Val"),
                                                ErlVarPattern.varPattern("_"))),
                                        ErlVar.var("Val")),
                                ErlClause.clause(
                                        List.of(ErlTuplePattern.tuplePattern(ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("_"))),
                                        ErlMap.map()))));
    }

    private static ErlRecordPattern recordBindingHead(String alias, String recordName, List<HttpBinding> bindings) {
        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (HttpBinding binding : bindings) {
            String field = BeamNameUtils.toSnakeCase(binding.getMember().getMemberName());
            fields.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(toBindingVar(field))));
        }
        return new ErlRecordPattern(recordName, fields, alias);
    }

    private static ErlRecordPattern httpRequestPattern(boolean streaming) {
        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        fields.add(ErlRecordFieldPattern.fieldPattern("query", ErlVarPattern.varPattern("Query")));
        fields.add(ErlRecordFieldPattern.fieldPattern("headers", ErlVarPattern.varPattern("Headers")));
        fields.add(ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));
        if (streaming) {
            fields.add(ErlRecordFieldPattern.fieldPattern("stream", ErlVarPattern.varPattern("Stream")));
        }
        return ErlRecordPattern.recordPattern("http_request", fields.toArray(ErlRecordFieldPattern[]::new));
    }

    private static ErlRecordPattern httpResponseErrorPattern() {
        return ErlRecordPattern.recordPattern(
                "http_response",
                ErlRecordFieldPattern.fieldPattern("status", ErlVarPattern.varPattern("Status")),
                ErlRecordFieldPattern.fieldPattern("headers", ErlVarPattern.varPattern("RespHeaders")),
                ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));
    }

    static ErlExpr decodeJsonExpr(
            Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, ErlExpr raw) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof EnumShape || target instanceof IntEnumShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("decode_" + helperName, raw);
        }
        if (target instanceof UnionShape union
                && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("decode_" + helperName, raw);
        }
        if (target instanceof StructureShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("decode_" + helperName, raw);
        }
        if (target instanceof TimestampShape) {
            String decodeHelper = timestampDecodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
            return ErlCallLocal.callLocal(decodeHelper, raw);
        }
        if (target instanceof ListShape listShape) {
            Shape element = model.expectShape(listShape.getMember().getTarget());
            if (element instanceof StructureShape) {
                String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
                return ErlCallLocal.callLocal("decode_" + helperName + "_list", raw);
            }
            String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
            return ErlCallLocal.callLocal(helper, raw);
        }
        if (target instanceof MapShape) {
            if (target.hasTrait(SparseTrait.class)) {
                return ErlCallLocal.callLocal("decode_sparse_map", raw);
            }
            return raw;
        }
        return raw;
    }

    static ErlExpr encodeJsonExpr(
            Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, String bindingVar) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof EnumShape || target instanceof IntEnumShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var(bindingVar));
        }
        if (target instanceof UnionShape union
                && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var(bindingVar));
        }
        if (target instanceof StructureShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var(bindingVar));
        }
        if (target instanceof TimestampShape) {
            String encodeHelper = timestampEncodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
            return ErlCallLocal.callLocal(encodeHelper, ErlVar.var(bindingVar));
        }
        if (target instanceof ListShape listShape) {
            Shape element = model.expectShape(listShape.getMember().getTarget());
            if (element instanceof StructureShape) {
                String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
                return ErlCallLocal.callLocal("encode_" + helperName + "_list", ErlVar.var(bindingVar));
            }
            if (target.hasTrait(SparseTrait.class)) {
                return ErlCallLocal.callLocal("encode_sparse_list", ErlVar.var(bindingVar));
            }
            return ErlVar.var(bindingVar);
        }
        if (target instanceof MapShape) {
            if (target.hasTrait(SparseTrait.class)) {
                return ErlCallLocal.callLocal("encode_sparse_map", ErlVar.var(bindingVar));
            }
            return ErlVar.var(bindingVar);
        }
        return ErlVar.var(bindingVar);
    }

    private static String headersWithChecksum(List<ErlExpr> body) {
        for (ErlExpr expr : body) {
            if (expr instanceof ErlCapturedBlock captured && captured.text().contains("HeadersWithChecksum")) {
                return "HeadersWithChecksum";
            }
        }
        return "Headers";
    }

    private static ErlExpr captureBody(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        writer.indent();
        action.accept(writer);
        String text = writer.toString().strip();
        return ErlCapturedBlock.capturedBlock(text);
    }

    private static List<ErlExpr> captureOptionalExprs(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        writer.indent();
        action.accept(writer);
        String text = writer.toString().strip();
        if (text.isEmpty()) {
            return List.of();
        }
        return List.of(ErlCapturedBlock.capturedBlock(text));
    }

    private static void emitRequestCompression(ErlangWriter writer, OperationShape op) {
        if (!supportsGzipCompression(op)) {
            return;
        }
        writer.write("Headers1 = Headers,");
        writer.write("{Body, Headers} = case byte_size(Body) >= 10240 of");
        writer.indent();
        writer.write("true ->");
        writer.indent();
        writer.write("Compressed = zlib:gzip(Body),");
        writer.write("{Compressed, headers_set(<<\"Content-Encoding\">>, <<\"gzip\">>, Headers1)};");
        writer.dedent();
        writer.write("false ->");
        writer.indent();
        writer.write("{Body, Headers1}");
        writer.dedent();
        writer.write("end,");
    }

    private static boolean supportsGzipCompression(OperationShape op) {
        return BeamRequestCompressionIndex.forOperation(op)
                .map(trait -> trait.getEncodings().stream()
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
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }

    private static String timestampEncodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        TimestampFormatTrait.Format fmt = httpIndex.determineTimestampFormat(
                member, location, TimestampFormatTrait.Format.DATE_TIME);
        return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "encode_timestamp_epoch_seconds"
                : "encode_timestamp_date_time";
    }

    private static String timestampDecodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        TimestampFormatTrait.Format fmt = httpIndex.determineTimestampFormat(
                member, location, TimestampFormatTrait.Format.DATE_TIME);
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
