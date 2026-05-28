package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.HttpTrait;

import java.util.List;

/**
 * Generates <app>_router.erl: a dispatch module that matches incoming HTTP
 * requests to server handler functions using the @http trait bindings.
 */
public final class ErlangRouterEmitter {

    private ErlangRouterEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (ctx.protocolCodegen() == null) {
            return;
        }
        Model model = ctx.model();
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(),
                service.getId().getNamespace());
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(model, service);
        String codecMod = layout.codecModuleName();
        String routerMod = layout.modulePrefix() + "_router";

        ctx.writerDelegator().useFileWriter(routerMod + ".erl", writer -> {
            writer.write("%% Generated HTTP router for $L.", service.getId());
            writer.write("-module($L).", routerMod);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
            writer.write("-export([dispatch/2]).");
            writer.write("");
            writer.write("%% @doc Routes an incoming HTTP request to the appropriate server handler.");
            writer.write("%% Handler is the module implementing the generated server behaviour.");
            writer.write("dispatch(Handler, #http_request{method = Method, path = Path} = Req) ->");
            writer.indent();
            writer.write("route(Method, Path, Handler, Req).");
            writer.dedent();
            writer.write("");

            for (OperationShape op : operations) {
                HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
                String method = httpTrait.getMethod().toUpperCase();
                String opName = sp.toSymbol(op).getName();
                String handlerFn = "handle_" + opName;

                List<HttpBinding> labels = httpIndex.getRequestBindings(op,
                        HttpBinding.Location.LABEL);

                String pathPattern = buildPathPattern(httpTrait.getUri().toString(), labels);

                writer.write("route(<<\"$L\">>, $L = Path, Handler, Req) ->", method, pathPattern);
                writer.indent();
                writer.write("Input = $L:decode_$L_request(Req),",
                        codecMod, opName);
                writer.write("Handler:$L(#{}, Input, #{});", handlerFn);
                writer.dedent();
            }

            writer.write("route(Method, Path, _Handler, _Req) ->");
            writer.indent();
            writer.write("{error, {not_found, Method, Path}}.");
            writer.dedent();
        });
    }

    private static String buildPathPattern(String uriTemplate, List<HttpBinding> labels) {
        if (labels.isEmpty()) {
            return "<<\"" + uriTemplate + "\">>";
        }
        return "Path";
    }

}
