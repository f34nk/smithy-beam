package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsJson10ProtocolCodegen;
import io.smithy.beam.core.BeamAwsJson11ProtocolCodegen;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpPathPatterns;
import io.smithy.beam.core.BeamProtocolSupport;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
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
        if (!BeamProtocolSupport.hasWireCodegen(
                ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations())) {
            return;
        }
        Model model = ctx.model();
        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(model, service);
        ShapeId protocol = ctx.resolvedProtocolTraitId();
        String codecMod = layout.serverCodecModuleName(protocol);
        String routerMod = layout.routerModuleName();
        String helpersMod = layout.runtimeHelpersModuleName();

        if (BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(protocol)
                || BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(protocol)) {
            emitAwsJsonRouter(ctx, service, layout, codecMod, routerMod, operations, sp);
            return;
        }

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
            writer.write(
                    "%% Handler must export handle_<operation>/3; typically $L after init_handlers/0.",
                    layout.serverModuleName());
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

    private static void emitAwsJsonRouter(
            ErlangContext ctx,
            ServiceShape service,
            BeamErlangLayout layout,
            String codecMod,
            String routerMod,
            List<OperationShape> operations,
            SymbolProvider sp) {

        String targetPrefix = service.getId().getName();

        ctx.writerDelegator().useFileWriter(layout.routerModuleFile(), writer -> {
            writer.write("%% Generated AWS JSON 1.0 router for $L.", service.getId());
            writer.write("-module($L).", routerMod);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
            writer.write("-export([dispatch/2]).");
            writer.write("");
            writer.write("%% @doc Routes POST / requests by X-Amz-Target header.");
            writer.write("dispatch(Handler, #http_request{method = Method, path = Path, headers = Headers} = Req) ->");
            writer.indent();
            writer.write("route(Method, Path, Headers, Handler, Req).");
            writer.dedent();
            writer.write("");

            writer.write("route(<<\"POST\">>, <<\"/\">>, Headers, Handler, Req) ->");
            writer.indent();
            writer.write("case proplists:get_value(<<\"X-Amz-Target\">>, Headers, undefined) of");
            writer.indent();
            for (OperationShape op : operations) {
                String opName = sp.toSymbol(op).getName();
                String handlerFn = "handle_" + opName;
                String amzTarget = targetPrefix + "." + op.getId().getName();
                writer.write("<<\"$L\">> ->", amzTarget);
                writer.indent();
                writer.write("Input = $L:decode_$L_request(Req),", codecMod, opName);
                writer.write("Handler:$L(#{}, Input, #{});", handlerFn);
                writer.dedent();
            }
            writer.write("_ ->");
            writer.indent();
            writer.write("{error, {not_found, <<\"POST\">>, <<\"/\">>}};");
            writer.dedent();
            writer.dedent();
            writer.write("end;");
            writer.dedent();
            writer.write("");

            writer.write("route(Method, Path, _Headers, _Handler, _Req) ->");
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
        writer.dedent();
        writer.write("end;");
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
