package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;
import java.util.function.Consumer;

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
        return capture(writer -> ErlangAwsJsonRpcEmitter.emitEncoder(
                writer, model, op, httpIndex, sp, targetPrefix, contentType, eventStreamModule));
    }

    static ErlFunction decodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String eventStreamModule) {
        return capture(writer -> ErlangAwsJsonRpcEmitter.emitDecoder(
                writer, model, op, httpIndex, sp, eventStreamModule));
    }

    static ErlFunction errorDispatch(Model model, OperationShape op, SymbolProvider sp) {
        return capture(writer -> ErlangAwsJsonRpcEmitter.emitErrorDispatch(writer, model, op, sp));
    }

    static ErlFunction decodeRequest(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String eventStreamModule) {
        return capture(writer -> ErlangAwsJsonRpcEmitter.emitRequestDecoder(
                writer, model, op, httpIndex, sp, eventStreamModule));
    }

    static ErlFunction encodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String contentType,
            String eventStreamModule) {
        return capture(writer -> ErlangAwsJsonRpcEmitter.emitResponseEncoder(
                writer, model, op, httpIndex, sp, contentType, eventStreamModule));
    }

    static ErlFunction sharedCodecHelpers(Model model, ServiceShape service, SymbolProvider sp) {
        return capture(writer -> ErlangRestJson1Emitter.emitSharedCodecHelpers(writer, model, service, sp));
    }

    static void writeFunction(ErlangWriter writer, ErlFunction fn) {
        writer.write("$L", fn.asString());
        writer.write("");
    }

    static void writeFunctions(ErlangWriter writer, List<ErlFunction> functions) {
        for (ErlFunction fn : functions) {
            writeFunction(writer, fn);
        }
    }

    private static ErlFunction capture(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        action.accept(writer);
        return ErlFunction.rendered(writer.toString().strip());
    }
}
