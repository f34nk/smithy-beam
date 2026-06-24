package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlFunction;

import java.util.List;
import java.util.function.Consumer;

final class ErlangHandlerDiscoveryIr {
    private ErlangHandlerDiscoveryIr() {}

    static List<String> discoveryMacros(BeamErlangLayout layout) {
        return List.of(
                "-define(DEFAULT_IMPL, " + layout.implModuleName() + ").",
                "-define(HANDLERS_KEY, {" + layout.serverModuleName() + ", handlers}).",
                "");
    }

    static ErlFunction resolveImpl(String behaviourMod) {
        return capture(writer -> ErlangHandlerDiscoveryEmitter.emitResolveImpl(writer, behaviourMod));
    }

    static ErlFunction makeHandler() {
        return capture(ErlangHandlerDiscoveryEmitter::emitMakeHandler);
    }

    static ErlFunction initHandlers() {
        return capture(ErlangHandlerDiscoveryEmitter::emitInitHandlers);
    }

    static ErlFunction dispatchHandler() {
        return capture(ErlangHandlerDiscoveryEmitter::emitDispatchHandler);
    }

    static ErlFunction operationDispatch(String handler) {
        return capture(writer -> ErlangHandlerDiscoveryEmitter.emitOperationDispatchBody(writer, handler));
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
