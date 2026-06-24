package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;
import io.smithy.beam.ir.erlang.ErlIntegerPattern;
import io.smithy.beam.ir.erlang.ErlPattern;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ErlangRestXmlOperationIr {
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
        String inputRecord = ErlangRestXmlEmitter.recordName(sp.toSymbol(input));
        String inputType = sp.toSymbol(input).getName();

        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> payloadMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
        List<HttpBinding> patternBindings =
                ErlangRestXmlEmitter.concat(labels, queries, queryParams, headers, prefixHeaders, payloadMembers);

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
                        captureBody(writer -> ErlangRestXmlEmitter.emitEncodeRequestBody(
                                writer, model, service, op, httpIndex, sp, encodeWithConfig)))));
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
                        captureBody(writer -> ErlangRestXmlEmitter.emitDecodeRequestBody(
                                writer, model, op, httpIndex, sp)))));
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
                captureBody(writer -> ErlangRestXmlEmitter.emitDecodeResponseSuccessBody(
                        writer, model, op, httpIndex, sp))));
        clauses.add(ErlClause.clause(
                fallbackPatterns,
                captureBody(writer -> ErlangRestXmlEmitter.emitDecodeResponseFallbackBody(writer, op, sp))));

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
                    ErlangRestXmlEmitter.buildResponseErrorDispatchClauses(model, op, sp)));
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
        String outputRecord = ErlangRestXmlEmitter.recordName(sp.toSymbol(output));
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
                        captureBody(writer -> ErlangRestXmlEmitter.emitEncodeResponseBody(
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
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        if (respPayload.isEmpty() && !output.members().isEmpty()) {
            for (MemberShape member : output.members()) {
                addBindingField(fields, member.getMemberName());
            }
        } else {
            for (HttpBinding binding : ErlangRestXmlEmitter.concat(respHeaders, respPrefixHeaders, respPayload)) {
                addBindingField(fields, binding.getMember().getMemberName());
            }
        }
        return new ErlRecordPattern(outputRecord, fields);
    }

    private static void addBindingField(List<ErlRecordFieldPattern> fields, String memberName) {
        String field = BeamNameUtils.toSnakeCase(memberName);
        fields.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(
                ErlangRestXmlEmitter.toBindingVar(field))));
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

    private static ErlExpr captureBody(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        writer.indent();
        action.accept(writer);
        String text = writer.toString().strip();
        return ErlCapturedBlock.capturedBlock(text);
    }
}
