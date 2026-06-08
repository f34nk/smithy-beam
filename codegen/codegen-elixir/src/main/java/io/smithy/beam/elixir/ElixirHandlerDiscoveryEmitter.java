package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;

/**
 * Emits startup handler discovery and dispatch helpers into {@code {service}_server.ex}.
 */
final class ElixirHandlerDiscoveryEmitter {

    private ElixirHandlerDiscoveryEmitter() {}

    /** Emit module attributes, resolve_impl/1, init_handlers/0, dispatch_handler/4. */
    static void emitDiscoveryHelpers(ElixirContext ctx, BeamElixirLayout layout) {
        String behaviourMod = ElixirSymbolProvider.toModuleName(layout.behaviourModuleName());
        String implMod = ElixirSymbolProvider.toModuleName(layout.implModuleName());
        String serverMod = ElixirSymbolProvider.toModuleName(layout.serverModuleName());

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushProtocolHookSection();
            writer.write("@default_impl $L", implMod);
            writer.write("@handlers_key {$L, :handlers}", serverMod);
            writer.write("");
            writer.write("defp resolve_impl(impl) do");
            writer.indent();
            writer.write("case Code.ensure_loaded(impl) do");
            writer.indent();
            writer.write("{:module, _} ->");
            writer.indent();
            writer.write("handlers =");
            writer.indent();
            writer.write("for {fun, 3} <- $L.callbacks(),", behaviourMod);
            writer.write("    function_exported?(impl, fun, 3),");
            writer.write("    into: %{} do");
            writer.indent();
            writer.write("{fun, Function.capture(impl, fun, 3)}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("");
            writer.write("{:ok, handlers}");
            writer.dedent();
            writer.write("");
            writer.write("{:error, _} ->");
            writer.indent();
            writer.write("{:error, {:impl_not_loaded, impl}}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("@spec init_handlers() :: :ok | {:error, term()}");
            writer.write("def init_handlers do");
            writer.indent();
            writer.write("case resolve_impl(@default_impl) do");
            writer.indent();
            writer.write("{:ok, handlers} ->");
            writer.indent();
            writer.write(":persistent_term.put(@handlers_key, handlers)");
            writer.write(":ok");
            writer.dedent();
            writer.write("");
            writer.write("{:error, reason} ->");
            writer.indent();
            writer.write(":persistent_term.put(@handlers_key, %{})");
            writer.write("{:error, reason}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp dispatch_handler(fun, ctx, input, meta) do");
            writer.indent();
            writer.write("handlers = :persistent_term.get(@handlers_key, %{})");
            writer.write("");
            writer.write("case Map.get(handlers, fun) do");
            writer.indent();
            writer.write("handler when is_function(handler, 3) ->");
            writer.indent();
            writer.write("handler.(ctx, input, meta)");
            writer.dedent();
            writer.write("");
            writer.write("_ ->");
            writer.indent();
            writer.write("{:error, :not_implemented}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.popState();
        });
    }

    /** Emit handle_<op>/3 body that calls dispatch_handler. */
    static void emitOperationDispatch(ElixirWriter writer, String handler) {
        writer.write("def $L(ctx, input, meta) do", handler);
        writer.indent();
        writer.write("dispatch_handler(:$L, ctx, input, meta)", handler);
        writer.dedent();
        writer.write("end");
        writer.write("");
    }
}
