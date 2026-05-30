package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpPathPatterns;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.HttpTrait;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a service-scoped router module that matches incoming HTTP
 * requests to server handler functions using the @http trait bindings.
 */
public final class ErlangRouterEmitter {

    private ErlangRouterEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (ctx.protocolCodegen() == null) {
            return;
        }
        Model model = ctx.model();
        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(model, service);
        String codecMod = layout.serverCodecModuleName();
        String routerMod = layout.routerModuleName();
        String helpersMod = layout.runtimeHelpersModuleName();

        List<OperationShape> literalOps = new ArrayList<>();
        List<OperationShape> labeledOps = new ArrayList<>();
        for (OperationShape op : operations) {
            List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
            if (labels.isEmpty()) {
                literalOps.add(op);
            } else {
                labeledOps.add(op);
            }
        }

        ctx.writerDelegator().useFileWriter(layout.routerModuleFile(), writer -> {
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

            for (OperationShape op : literalOps) {
                emitRoute(writer, op, httpIndex, sp, codecMod, helpersMod, false);
            }
            for (OperationShape op : labeledOps) {
                emitRoute(writer, op, httpIndex, sp, codecMod, helpersMod, true);
            }

            writer.write("route(Method, Path, _Handler, _Req) ->");
            writer.indent();
            writer.write("{error, {not_found, Method, Path}}.");
            writer.dedent();
        });
    }

    private static void emitRoute(
            ErlangWriter writer,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String codecMod,
            String helpersMod,
            boolean labeled) {

        HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
        String method = httpTrait.getMethod().toUpperCase();
        String uriTemplate = httpTrait.getUri().toString();
        String opName = sp.toSymbol(op).getName();
        String handlerFn = "handle_" + opName;
        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);

        String pathPattern = buildErlangPathMatchPattern(uriTemplate, labels);
        String guard = labeled ? singleTrailingLabelGuard(uriTemplate) : "";

        writer.write("route(<<\"$L\">>, $L = Path, Handler, Req)$L ->", method, pathPattern, guard);
        writer.indent();

        if (labeled) {
            emitLabeledRouteBody(writer, helpersMod, uriTemplate, codecMod, opName, handlerFn, method);
        } else {
            writer.write("Input = $L:decode_$L_request(Req),", codecMod, opName);
            writer.write("Handler:$L(#{}, Input, #{});", handlerFn);
        }

        writer.dedent();
    }

    private static void emitLabeledRouteBody(
            ErlangWriter writer,
            String helpersMod,
            String uriTemplate,
            String codecMod,
            String opName,
            String handlerFn,
            String method) {

        writer.write("case $L:parse_labels(Path, <<\"$L\">>) of", helpersMod, uriTemplate);
        writer.indent();
        writer.write("{ok, LabelMap} ->");
        writer.indent();
        writer.write("Input = $L:decode_$L_request(Req, LabelMap),", codecMod, opName);
        writer.write("Handler:$L(#{}, Input, #{});", handlerFn);
        writer.dedent();
        writer.write("{error, path_mismatch} ->");
        writer.indent();
        writer.write("{error, {not_found, <<\"$L\">>, Path}}", method);
        writer.dedent();
        writer.write("end;");
        writer.dedent();
    }

    private static String buildErlangPathMatchPattern(String uriTemplate, List<HttpBinding> labels) {
        if (labels.isEmpty()) {
            return "<<\"" + uriTemplate + "\">>";
        }
        StringBuilder sb = new StringBuilder("<<");
        int labelIndex = 0;
        List<BeamHttpPathPatterns.PathSegment> segments = BeamHttpPathPatterns.parseTemplate(uriTemplate);
        for (BeamHttpPathPatterns.PathSegment seg : segments) {
            if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
                String var = labelVarName(labelIndex++);
                sb.append(", ").append(var).append("/binary");
            } else {
                sb.append("\"").append(seg.value()).append("\"");
            }
        }
        sb.append(">>");
        return sb.toString();
    }

    private static String labelVarName(int index) {
        return index == 0 ? "NameSeg" : "LabelSeg" + index;
    }

    private static String trailingLabelVarName(String uriTemplate) {
        List<BeamHttpPathPatterns.PathSegment> segments = BeamHttpPathPatterns.parseTemplate(uriTemplate);
        if (segments.isEmpty()
                || segments.get(segments.size() - 1).kind() != BeamHttpPathPatterns.SegmentKind.LABEL) {
            return null;
        }
        int labelCount = 0;
        for (BeamHttpPathPatterns.PathSegment seg : segments) {
            if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
                labelCount++;
            }
        }
        return labelVarName(labelCount - 1);
    }

    private static String singleTrailingLabelGuard(String uriTemplate) {
        String var = trailingLabelVarName(uriTemplate);
        if (var == null) {
            return "";
        }
        return " when " + var + " =/= <<>>";
    }
}
