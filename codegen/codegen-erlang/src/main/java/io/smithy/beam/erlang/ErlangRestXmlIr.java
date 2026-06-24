package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ErlangRestXmlIr {
    private ErlangRestXmlIr() {}

    static ErlFunction encodeRequest(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            boolean encodeWithConfig) {
        return capture(writer -> ErlangRestXmlEmitter.emitEncoder(
                writer, model, service, op, httpIndex, sp, encodeWithConfig));
    }

    static ErlFunction decodeRequest(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return capture(writer -> ErlangRestXmlEmitter.emitRequestDecoder(
                writer, model, op, httpIndex, sp));
    }

    static ErlFunction decodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return capture(writer -> ErlangRestXmlEmitter.emitDecoder(writer, model, op, httpIndex, sp));
    }

    static ErlFunction encodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return capture(writer -> ErlangRestXmlEmitter.emitResponseEncoder(
                writer, model, op, httpIndex, sp));
    }

    static List<ErlFunction> enumHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        for (EnumShape enumShape : ErlangRestXmlEmitter.reachableEnumShapes(model, service)) {
            functions.addAll(ErlangEnumHelperIr.enumDecodeEncode(enumShape, sp));
        }
        for (IntEnumShape intEnumShape : ErlangRestXmlEmitter.reachableIntEnumShapes(model, service)) {
            functions.addAll(ErlangEnumHelperIr.intEnumDecodeEncode(intEnumShape, sp));
        }
        return functions;
    }

    static ErlFunction xmlDecodeHelpers() {
        return capture(ErlangRestXmlEmitter::emitXmlDecodeHelpers);
    }

    static ErlFunction xmlEncodeHelpers() {
        return capture(ErlangRestXmlEmitter::emitXmlEncodeHelpers);
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
