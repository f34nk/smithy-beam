package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.ArrayList;
import java.util.List;

final class ErlangAwsJsonIr {
    private ErlangAwsJsonIr() {}

    static ErlFunction encodeRequest(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String targetPrefix,
            String contentType,
            String eventStreamModule) {
        return ErlangAwsJsonOperationIr.buildEncodeRequest(
                model, op, httpIndex, sp, targetPrefix, contentType, eventStreamModule);
    }

    static ErlFunction decodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String eventStreamModule) {
        return ErlangAwsJsonOperationIr.buildDecodeResponse(
                model, op, httpIndex, sp, eventStreamModule);
    }

    static ErlFunction errorDispatch(Model model, OperationShape op, SymbolProvider sp) {
        return ErlangAwsJsonOperationIr.buildErrorDispatch(model, op, sp);
    }

    static ErlFunction decodeRequest(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String eventStreamModule) {
        return ErlangAwsJsonOperationIr.buildDecodeRequest(
                model, op, httpIndex, sp, eventStreamModule);
    }

    static ErlFunction encodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String contentType,
            String eventStreamModule) {
        return ErlangAwsJsonOperationIr.buildEncodeResponse(
                model, op, httpIndex, sp, contentType, eventStreamModule);
    }

    static List<ErlFunction> sharedCodecHelpers(Model model, ServiceShape service, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        functions.addAll(ErlangRestJsonIr.structureHelperFunctions(model, service, sp));
        functions.addAll(ErlangRestJsonIr.enumHelperFunctions(model, service, sp));
        functions.addAll(ErlangRestJsonIr.unionHelperFunctions(model, service, sp));
        functions.addAll(ErlangRestJsonIr.privateCodecHelpers(model, service));
        return functions;
    }

    static List<ErlFunction> clientCodecFunctions(
            Model model,
            ServiceShape service,
            List<OperationShape> operations,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String targetPrefix,
            String contentType,
            String eventStreamModule) {
        List<ErlFunction> functions = new ArrayList<>();
        for (OperationShape op : operations) {
            functions.add(encodeRequest(
                    model, op, httpIndex, sp, targetPrefix, contentType, eventStreamModule));
            functions.add(decodeResponse(model, op, httpIndex, sp, eventStreamModule));
        }
        for (OperationShape op : operations) {
            functions.add(errorDispatch(model, op, sp));
        }
        functions.addAll(sharedCodecHelpers(model, service, sp));
        return functions;
    }

    static List<ErlFunction> serverCodecFunctions(
            Model model,
            ServiceShape service,
            List<OperationShape> operations,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String contentType,
            String eventStreamModule) {
        List<ErlFunction> functions = new ArrayList<>();
        for (OperationShape op : operations) {
            functions.add(decodeRequest(model, op, httpIndex, sp, eventStreamModule));
            functions.add(encodeResponse(
                    model, op, httpIndex, sp, contentType, eventStreamModule));
        }
        functions.addAll(sharedCodecHelpers(model, service, sp));
        return functions;
    }
}
