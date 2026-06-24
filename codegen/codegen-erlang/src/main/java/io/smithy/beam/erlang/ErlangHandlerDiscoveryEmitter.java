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

    static void emitResolveImpl(ErlangWriter writer, String behaviourMod) {
        writer.write("resolve_impl(Impl) ->");
        writer.indent();
        writer.write("case code:ensure_loaded(Impl) of");
        writer.indent();
        writer.write("{module, Impl} ->");
        writer.indent();
        writer.write("Callbacks = $L:behaviour_info(callbacks),", behaviourMod);
        writer.write("Handlers = maps:from_list([");
        writer.indent();
        writer.write("{Fun, make_handler(Impl, Fun)}");
        writer.write(" || {Fun, 3} <- Callbacks,");
        writer.write("    erlang:function_exported(Impl, Fun, 3)");
        writer.dedent();
        writer.write("]),");
        writer.write("{ok, Handlers};");
        writer.dedent();
        writer.write("{error, _} ->");
        writer.indent();
        writer.write("{error, {impl_not_loaded, Impl}}");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
    }

    static void emitMakeHandler(ErlangWriter writer) {
        writer.write("make_handler(Impl, Fun) ->");
        writer.indent();
        writer.write("fun(Ctx, Input, Meta) -> Impl:Fun(Ctx, Input, Meta) end.");
        writer.dedent();
    }

    static void emitInitHandlers(ErlangWriter writer) {
        writer.write("-spec init_handlers() -> ok | {error, term()}.");
        writer.write("init_handlers() ->");
        writer.indent();
        writer.write("case resolve_impl(?DEFAULT_IMPL) of");
        writer.indent();
        writer.write("{ok, Handlers} ->");
        writer.indent();
        writer.write("persistent_term:put(?HANDLERS_KEY, Handlers),");
        writer.write("ok;");
        writer.dedent();
        writer.write("{error, Reason} ->");
        writer.indent();
        writer.write("persistent_term:put(?HANDLERS_KEY, #{}),");
        writer.write("{error, Reason}");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
    }

    static void emitDispatchHandler(ErlangWriter writer) {
        writer.write("dispatch_handler(Fun, Ctx, Input, Meta) ->");
        writer.indent();
        writer.write("Handlers = persistent_term:get(?HANDLERS_KEY, #{}),");
        writer.write("case maps:get(Fun, Handlers, undefined) of");
        writer.indent();
        writer.write("Handler when is_function(Handler, 3) ->");
        writer.indent();
        writer.write("Handler(Ctx, Input, Meta);");
        writer.dedent();
        writer.write("_ ->");
        writer.indent();
        writer.write("{error, not_implemented}");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
    }

    static void emitOperationDispatchBody(ErlangWriter writer, String handler) {
        writer.write("$L(Ctx, Input, Meta) ->", handler);
        writer.indent();
        writer.write("dispatch_handler($L, Ctx, Input, Meta).", handler);
        writer.dedent();
    }
}
