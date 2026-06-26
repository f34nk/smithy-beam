package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;

/**
 * Emits startup handler discovery and dispatch helpers into {@code {service}_server.erl}.
 */
final class ErlangHandlerDiscoveryEmitter {

    private ErlangHandlerDiscoveryEmitter() {}

    /** Emit -define, resolve_impl/1, make_handler/2, init_handlers/0, dispatch_handler/4. */
    static void emitDiscoveryHelpers(ErlangContext ctx, BeamErlangLayout layout) {
        String behaviourMod = layout.behaviourModuleName();

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushProtocolHookSection();
            for (String line : ErlangHandlerDiscoveryIr.discoveryMacros(layout)) {
                if (line.isEmpty()) {
                    writer.write("");
                } else {
                    writer.write(line);
                }
            }
            ErlangHandlerDiscoveryIr.writeFunction(writer, ErlangHandlerDiscoveryIr.resolveImpl(behaviourMod));
            ErlangHandlerDiscoveryIr.writeFunction(writer, ErlangHandlerDiscoveryIr.makeHandler());
            ErlangHandlerDiscoveryIr.writeFunction(writer, ErlangHandlerDiscoveryIr.initHandlers());
            ErlangHandlerDiscoveryIr.writeFunction(writer, ErlangHandlerDiscoveryIr.dispatchHandler());
            writer.popState();
        });
    }

    /** Emit handle_<op>/3 body that calls dispatch_handler. */
    static void emitOperationDispatch(ErlangWriter writer, String handler) {
        ErlangHandlerDiscoveryIr.writeFunction(writer, ErlangHandlerDiscoveryIr.operationDispatch(handler));
    }
}
