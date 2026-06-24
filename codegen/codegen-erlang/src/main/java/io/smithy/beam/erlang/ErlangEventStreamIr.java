package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.UnionShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ErlangEventStreamIr {
    private ErlangEventStreamIr() {}

    static ErlFunction encodeEventHeaders() {
        return capture(ErlangEventStreamEmitter::emitEncodeEventHeaders);
    }

    static ErlFunction headerValue() {
        return capture(ErlangEventStreamEmitter::emitHeaderValue);
    }

    static List<ErlFunction> unionHelpers(Model model, UnionShape union, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(capture(writer -> ErlangEventStreamEmitter.emitUnionEncodeList(writer, union, sp)));
        functions.add(capture(writer -> ErlangEventStreamEmitter.emitUnionDecodeList(writer, union, sp)));
        functions.addAll(captureUnionMemberClauses(model, union, sp));
        return functions;
    }

    static void writeFunctions(ErlangWriter writer, List<ErlFunction> functions) {
        for (ErlFunction fn : functions) {
            writer.write("$L", fn.asString());
            writer.write("");
        }
    }

    private static List<ErlFunction> captureUnionMemberClauses(Model model, UnionShape union, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(capture(writer -> ErlangEventStreamEmitter.emitUnionEncodeEventClauses(writer, model, union, sp)));
        functions.add(capture(writer -> ErlangEventStreamEmitter.emitUnionDecodeEvent(writer, union, sp)));
        functions.add(capture(writer -> ErlangEventStreamEmitter.emitUnionDecodeEventTypeClauses(writer, model, union, sp)));
        return functions;
    }

    private static ErlFunction capture(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        action.accept(writer);
        return ErlFunction.rendered(writer.toString().strip());
    }
}
