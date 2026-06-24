package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;

import java.util.function.Consumer;

final class ErlangHttpDispatchIr {
    private ErlangHttpDispatchIr() {}

    static ErlFunction dispatchArity2() {
        return capture(ErlangHttpDispatchEmitter::emitDispatchArity2);
    }

    static ErlFunction dispatchArity3() {
        return capture(ErlangHttpDispatchEmitter::emitDispatchArity3);
    }

    static ErlFunction dispatchSigned(
            boolean sigv4,
            boolean endpointRules,
            String configVar,
            String helpersMod,
            String endpointsMod,
            String credentialsMod) {
        return capture(writer -> ErlangHttpDispatchEmitter.emitDispatchSigned(
                writer, sigv4, endpointRules, configVar, helpersMod, endpointsMod, credentialsMod));
    }

    static ErlFunction splitBaseUrl() {
        return capture(ErlangHttpDispatchEmitter::emitSplitBaseUrl);
    }

    static ErlFunction mime() {
        return capture(ErlangHttpDispatchEmitter::emitMimeHelper);
    }

    static void writeFunction(ErlangWriter writer, ErlFunction fn) {
        writer.write("$L", fn.asString());
        writer.write("");
    }

    private static ErlFunction capture(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        action.accept(writer);
        return ErlFunction.rendered(writer.toString().strip());
    }
}
