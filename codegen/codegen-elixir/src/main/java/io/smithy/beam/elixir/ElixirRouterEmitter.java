package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.HttpTrait;

import java.util.List;

/**
 * Generates {@code <App>Router}: a dispatch module that routes HTTP requests to server handlers.
 */
public final class ElixirRouterEmitter {

    private ElixirRouterEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        if (ctx.protocolCodegen() == null) {
            return;
        }
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), service.getId().getNamespace());
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
        String routerMod = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_router");
        String codecMod = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_rest_json_1");

        ctx.writerDelegator().useFileWriter(layout.modulePrefix() + "_router.ex", writer -> {
            writer.write("defmodule $L do", routerMod);
            writer.indent();
            writer.write("@moduledoc \"Generated HTTP router for $L.\"", service.getId());
            writer.write("");
            writer.write("@spec dispatch(module(), map()) :: term()");
            writer.write("def dispatch(handler, request) do");
            writer.indent();
            writer.write("route(request.method, request.path, handler, request)");
            writer.dedent();
            writer.write("end");
            writer.write("");

            for (OperationShape op : operations) {
                HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
                String method = httpTrait.getMethod().toUpperCase();
                String opName = sp.toSymbol(op).getName();
                List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
                String pathPattern = buildPathPattern(httpTrait.getUri().toString(), labels);

                writer.write("defp route(\"$L\", $L, handler, request) do", method, pathPattern);
                writer.indent();
                writer.write("input = $L.decode_$L_request(request)", codecMod, opName);
                writer.write("handler.handle_$L(%{}, input, %{})", opName);
                writer.dedent();
                writer.write("end");
                writer.write("");
            }

            writer.write("defp route(method, path, _handler, _request) do");
            writer.indent();
            writer.write("{:error, {:not_found, method, path}}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }

    private static String buildPathPattern(String uriTemplate, List<HttpBinding> labels) {
        if (labels.isEmpty()) {
            return "\"" + escapeElixirString(uriTemplate) + "\"";
        }
        return "path";
    }

    private static String escapeElixirString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
