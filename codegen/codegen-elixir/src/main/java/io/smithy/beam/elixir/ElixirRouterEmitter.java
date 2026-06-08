package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsJson10ProtocolCodegen;
import io.smithy.beam.core.BeamAwsJson11ProtocolCodegen;
import io.smithy.beam.core.BeamElixirLayout;
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
 * Generates {@code <App>Router}: a dispatch module that routes HTTP requests to server handlers.
 */
public final class ElixirRouterEmitter {

    private ElixirRouterEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        if (!BeamProtocolSupport.hasWireCodegen(
                ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations())) {
            return;
        }
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
        ShapeId protocol = ctx.resolvedProtocolTraitId();
        String routerMod = ElixirSymbolProvider.toModuleName(layout.routerModuleName());
        String codecMod = ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
        String helpersMod = ElixirSymbolProvider.toModuleName(layout.runtimeHelpersModuleName());

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
            writer.write("defmodule $L do", routerMod);
            writer.indent();
            writer.write("@moduledoc \"Generated HTTP router for $L.\"", service.getId());
            writer.write("");
            writer.write(
                    "# Handler must export handle_<operation>/3; typically $L after init_handlers/0.",
                    ElixirSymbolProvider.toModuleName(layout.serverModuleName()));
            writer.write("");
            ElixirFormat.writeSpec(writer, "@spec", "dispatch", "module(), map()", "term()");
            writer.write("def dispatch(handler, request) do");
            writer.indent();
            writer.write("route(request.method, request.path, handler, request)");
            writer.dedent();
            writer.write("end");
            writer.write("");

            for (OperationShape op : literalOps) {
                emitRoute(writer, op, httpIndex, sp, codecMod, helpersMod, false);
            }
            for (OperationShape op : labeledOps) {
                emitRoute(writer, op, httpIndex, sp, codecMod, helpersMod, true);
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

    private static void emitAwsJsonRouter(
            ElixirContext ctx,
            ServiceShape service,
            BeamElixirLayout layout,
            String codecMod,
            String routerMod,
            List<OperationShape> operations,
            SymbolProvider sp) {

        String targetPrefix = service.getId().getName();

        ctx.writerDelegator().useFileWriter(layout.routerModuleFile(), writer -> {
            writer.write("defmodule $L do", routerMod);
            writer.indent();
            writer.write("@moduledoc \"Generated AWS JSON router for $L.\"", service.getId());
            writer.write("");
            writer.write(
                    "# Handler must export handle_<operation>/3; typically $L after init_handlers/0.",
                    ElixirSymbolProvider.toModuleName(layout.serverModuleName()));
            writer.write("");
            ElixirFormat.writeSpec(writer, "@spec", "dispatch", "module(), map()", "term()");
            writer.write("def dispatch(handler, request) do");
            writer.indent();
            writer.write("route(request.method, request.path, request.headers, handler, request)");
            writer.dedent();
            writer.write("end");
            writer.write("");

            writer.write("defp route(\"POST\", \"/\", headers, handler, request) do");
            writer.indent();
            writer.write("case List.keyfind(headers, \"X-Amz-Target\", 0) do");
            writer.indent();
            for (OperationShape op : operations) {
                String opName = sp.toSymbol(op).getName();
                String amzTarget = targetPrefix + "." + op.getId().getName();
                writer.write("{_, \"$L\"} ->", amzTarget);
                writer.indent();
                writer.write("input = $L.decode_$L_request(request)", codecMod, opName);
                writer.write("handler.handle_$L(%{}, input, %{})", opName);
                writer.dedent();
            }
            writer.write("_ ->");
            writer.indent();
            writer.write("{:error, {:not_found, \"POST\", \"/\"}}");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");

            writer.write("defp route(method, path, _headers, _handler, _request) do");
            writer.indent();
            writer.write("{:error, {:not_found, method, path}}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }

    private static void emitRoute(
            ElixirWriter writer,
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
        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);

        String pathPattern = buildElixirPathMatchPattern(uriTemplate, labels);
        String guard = labeled ? singleTrailingLabelGuard(uriTemplate) : "";

        writer.write("defp route(\"$L\", $L, handler, request)$L do", method, pathPattern, guard);
        writer.indent();

        if (labeled) {
            emitLabeledRouteBody(writer, helpersMod, uriTemplate, codecMod, opName, method);
        } else {
            writer.write("input = $L.decode_$L_request(request)", codecMod, opName);
            writer.write("handler.handle_$L(%{}, input, %{})", opName);
        }

        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitLabeledRouteBody(
            ElixirWriter writer,
            String helpersMod,
            String uriTemplate,
            String codecMod,
            String opName,
            String method) {

        writer.write("case $L.parse_labels(path, \"$L\") do", helpersMod, escapeElixirString(uriTemplate));
        writer.indent();
        writer.write("{:ok, label_map} ->");
        writer.indent();
        writer.write("input = $L.decode_$L_request(request, label_map)", codecMod, opName);
        writer.write("handler.handle_$L(%{}, input, %{})", opName);
        writer.dedent();
        writer.write("");
        writer.write("{:error, :path_mismatch} ->");
        writer.indent();
        writer.write("{:error, {:not_found, \"$L\", path}}", method);
        writer.dedent();
        writer.dedent();
        writer.write("end");
    }

    private static String buildElixirPathMatchPattern(String uriTemplate, List<HttpBinding> labels) {
        if (labels.isEmpty()) {
            return "\"" + escapeElixirString(uriTemplate) + "\"";
        }
        StringBuilder sb = new StringBuilder();
        int labelIndex = 0;
        List<BeamHttpPathPatterns.PathSegment> segments = BeamHttpPathPatterns.parseTemplate(uriTemplate);
        for (BeamHttpPathPatterns.PathSegment seg : segments) {
            if (seg.kind() == BeamHttpPathPatterns.SegmentKind.LABEL) {
                sb.append(" <> ").append(labelVarName(labelIndex++));
            } else {
                if (sb.isEmpty()) {
                    sb.append("\"").append(escapeElixirString(seg.value())).append("\"");
                } else {
                    sb.append(" <> \"").append(escapeElixirString(seg.value())).append("\"");
                }
            }
        }
        sb.append(" = path");
        return sb.toString();
    }

    private static String labelVarName(int index) {
        return index == 0 ? "name_seg" : "label_seg" + index;
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
        return " when " + var + " != \"\"";
    }

    private static String escapeElixirString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
