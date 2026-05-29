package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamNameUtils;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpTrait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST JSON 1 codec emitter for Elixir. Generates encode_request and decode_response
 * functions per operation using Jason for JSON and Req for HTTP.
 */
public final class ElixirRestJson1Emitter {

    private ElixirRestJson1Emitter() {}

    public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), service.getId().getNamespace());
        ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        String moduleName = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_rest_json_1");
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_runtime_types");
        String typesMod = ElixirSymbolProvider.toModuleName(layout.modulePrefix());
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

        ctx.writerDelegator().useFileWriter(layout.codecModuleFile(), writer -> {
            writer.write("defmodule $L do", moduleName);
            writer.indent();
            writer.write("@moduledoc \"REST JSON 1 codecs for $L (generated). Do not edit.\"",
                    service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            writer.write("");

            for (OperationShape op : operations) {
                emitEncoder(writer, model, op, httpIndex, sp, typesMod, runtimeMod);
                emitRequestDecoder(writer, model, op, httpIndex, sp, typesMod);
                emitDecoder(writer, model, op, httpIndex, sp, typesMod);
            }

            for (OperationShape op : operations) {
                emitErrorDispatch(writer, model, op, sp, typesMod);
            }

            emitHelpers(writer);

            writer.dedent();
            writer.write("end");
        });
    }

    private static void emitEncoder(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod,
            String runtimeMod) {

        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
        String method = httpTrait.getMethod();
        String uriTemplate = httpTrait.getUri().toString();

        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);

        String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
        String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";

        writer.write("@spec encode_$L_request($L) :: $L", opName, inputType, httpRequestType);
        writer.write("def encode_$L_request(input) do", opName);
        writer.indent();

        String pathExpr = buildElixirPathExpression(uriTemplate, labels, sp);
        writer.write("path = $L", pathExpr);

        if (!queries.isEmpty()) {
            writer.write("query = %{");
            for (HttpBinding qb : queries) {
                String field = fieldName(sp, qb.getMember());
                writer.write("  \"$L\" => input.$L,", qb.getLocationName(), field);
            }
            writer.write("}");
            writer.write("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
            writer.write("|> Map.new()");
        } else {
            writer.write("query = %{}");
        }

        if (!headers.isEmpty()) {
            writer.write("extra_headers = [");
            for (HttpBinding hb : headers) {
                String field = fieldName(sp, hb.getMember());
                writer.write("  (if input.$L != nil, do: {\"$L\", to_string(input.$L)}, else: nil),",
                        field, hb.getLocationName(), field);
            }
            writer.write("] |> Enum.reject(&is_nil/1)");
            writer.write("headers = [{\"Content-Type\", \"application/json\"} | extra_headers]");
        } else {
            writer.write("headers = [{\"Content-Type\", \"application/json\"}]");
        }

        boolean hasBody = !docMembers.isEmpty()
                && !method.equals("GET") && !method.equals("DELETE") && !method.equals("HEAD");
        if (hasBody) {
            writer.write("body_map = %{");
            for (HttpBinding db : docMembers) {
                String field = fieldName(sp, db.getMember());
                writer.write("  \"$L\" => input.$L,", db.getMember().getMemberName(), field);
            }
            writer.write("}");
            writer.write("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
            writer.write("|> Map.new()");
            writer.write("body = Jason.encode!(body_map)");
        } else {
            writer.write("body = \"\"");
        }

        writer.write("%RuntimeTypes.HttpRequest{");
        writer.write("  method: \"$L\",", method);
        writer.write("  path: path,");
        writer.write("  query: query,");
        writer.write("  headers: headers,");
        writer.write("  body: body");
        writer.write("}");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitRequestDecoder(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod) {

        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputStruct = sp.toSymbol(input).getName();

        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);

        if (labels.isEmpty()) {
            writer.write(
                    "def decode_$L_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}) do",
                    opName);
        } else {
            writer.write(
                    "def decode_$L_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}, label_map) do",
                    opName);
        }
        writer.indent();
        emitRequestDecoderStruct(writer, labels, queries, headers, docMembers, sp, inputStruct);
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitRequestDecoderStruct(
            ElixirWriter writer,
            List<HttpBinding> labels,
            List<HttpBinding> queries,
            List<HttpBinding> headers,
            List<HttpBinding> docMembers,
            SymbolProvider sp,
            String inputStruct) {

        if (!docMembers.isEmpty()) {
            writer.write("decoded = if body == \"\" or is_nil(body), do: %{}, else: Jason.decode!(body)");
        }

        writer.write("%Types.$L{", inputStruct);
        for (HttpBinding lb : labels) {
            String field = fieldName(sp, lb.getMember());
            String memberName = lb.getMember().getMemberName();
            writer.write("  $L: uri_decode(Map.get(label_map, \"$L\")),", field, memberName);
        }
        for (HttpBinding qb : queries) {
            String field = fieldName(sp, qb.getMember());
            writer.write("  $L: decode_query_param(Map.get(query, \"$L\")),", field, qb.getLocationName());
        }
        for (HttpBinding hb : headers) {
            String field = fieldName(sp, hb.getMember());
            writer.write("  $L: List.keyfind(headers, \"$L\", 0) |> case do", field, hb.getLocationName());
            writer.indent();
            writer.write("{_, v} -> v");
            writer.write("nil -> nil");
            writer.dedent();
            writer.write("end,");
        }
        for (HttpBinding db : docMembers) {
            String field = fieldName(sp, db.getMember());
            String jsonKey = db.getMember().getMemberName();
            writer.write("  $L: Map.get(decoded, \"$L\"),", field, jsonKey);
        }
        writer.write("}");
    }

    private static void emitDecoder(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod) {

        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputStruct = sp.toSymbol(output).getName();
        int successCode = httpIndex.getResponseCode(op);

        List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

        writer.write(
                "def decode_$L_response(%RuntimeTypes.HttpResponse{status: $L, headers: headers, body: body}) do",
                opName, successCode);
        writer.indent();

        if (!respDoc.isEmpty()) {
            writer.write("decoded = if body == \"\" or is_nil(body), do: %{}, else: Jason.decode!(body)");
        }

        for (HttpBinding hb : respHeaders) {
            String field = fieldName(sp, hb.getMember());
            writer.write("$L = List.keyfind(headers, \"$L\", 0) |> case do", field, hb.getLocationName());
            writer.indent();
            writer.write("{_, v} -> v");
            writer.write("nil -> nil");
            writer.dedent();
            writer.write("end");
        }

        writer.write("{:ok, %Types.$L{", outputStruct);
        for (HttpBinding hb : respHeaders) {
            String field = fieldName(sp, hb.getMember());
            writer.write("  $L: $L,", field, field);
        }
        for (HttpBinding db : respDoc) {
            String field = fieldName(sp, db.getMember());
            String jsonKey = db.getMember().getMemberName();
            writer.write("  $L: Map.get(decoded, \"$L\"),", field, jsonKey);
        }
        for (HttpBinding pb : respPayload) {
            String field = fieldName(sp, pb.getMember());
            writer.write("  $L: body,", field);
        }
        writer.write("}}");

        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write(
                "def decode_$L_response(%RuntimeTypes.HttpResponse{status: status, headers: headers, body: body}) do",
                opName);
        writer.indent();
        writer.write("decode_$L_response_error(status, headers, body)", opName);
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitErrorDispatch(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            SymbolProvider sp,
            String typesMod) {

        String opName = sp.toSymbol(op).getName();
        List<ShapeId> errors = new ArrayList<>(op.getErrors());

        writer.write("# Error dispatch for $L", op.getId());
        for (ShapeId errorId : errors) {
            StructureShape errShape = model.expectShape(errorId, StructureShape.class);
            String modName = sp.toSymbol(errShape).getName();
            int httpStatus = errShape.hasTrait(HttpErrorTrait.class)
                    ? errShape.expectTrait(HttpErrorTrait.class).getCode()
                    : -1;
            if (httpStatus <= 0) {
                continue;
            }
            writer.write("defp decode_$L_response_error($L, _headers, body) do", opName, httpStatus);
            writer.indent();
            writer.write("decoded = decode_json_body(body)");
            List<String> fields = buildErrorFields(model, errShape, sp);
            writer.write("{:error, struct!($L.$L, %{$L})}", typesMod, modName,
                    String.join(", ", fields));
            writer.dedent();
            writer.write("end");
            writer.write("");
        }

        boolean hasTypeDiscriminated = errors.stream().anyMatch(e ->
                !model.expectShape(e, StructureShape.class).hasTrait(HttpErrorTrait.class));

        if (hasTypeDiscriminated) {
            writer.write("defp decode_$L_response_error(status, _headers, body) when status >= 400 do", opName);
            writer.indent();
            writer.write("decoded = decode_json_body(body)");
            writer.write("error_type = Map.get(decoded, \"__type\")");
            writer.write("case error_type do");
            writer.indent();
            for (ShapeId errorId : errors) {
                StructureShape errShape = model.expectShape(errorId, StructureShape.class);
                if (errShape.hasTrait(HttpErrorTrait.class)) {
                    continue;
                }
                String modName = sp.toSymbol(errShape).getName();
                String localName = errorId.getName();
                List<String> fields = buildErrorFields(model, errShape, sp);
                writer.write("\"$L\" ->", localName);
                writer.indent();
                writer.write("{:error, struct!($L.$L, %{$L})}", typesMod, modName,
                        String.join(", ", fields));
                writer.dedent();
            }
            writer.write("_ -> {:error, {:unknown_error, status, body}}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
        } else {
            writer.write("defp decode_$L_response_error(status, _headers, body) do", opName);
            writer.indent();
            writer.write("{:error, {:unknown_error, status, body}}");
            writer.dedent();
            writer.write("end");
            writer.write("");
        }
    }

    private static List<String> buildErrorFields(Model model, StructureShape errShape, SymbolProvider sp) {
        List<String> fields = new ArrayList<>();
        for (MemberShape member : errShape.members()) {
            if (member.getMemberName().equals("__beam_error_kind")) {
                continue;
            }
            String field = fieldName(sp, member);
            String jsonKey = member.getMemberName();
            fields.add(field + ": Map.get(decoded, \"" + jsonKey + "\")");
        }
        return fields;
    }

    private static void emitHelpers(ElixirWriter writer) {
        writer.write("# -- Private helpers --");
        writer.write("");
        writer.write("defp uri_encode(value), do: URI.encode(to_string(value))");
        writer.write("");
        writer.write("defp uri_decode(nil), do: nil");
        writer.write("defp uri_decode(value), do: URI.decode(value)");
        writer.write("");
        writer.write("defp decode_query_param(nil), do: nil");
        writer.write("defp decode_query_param(true), do: true");
        writer.write("defp decode_query_param(false), do: false");
        writer.write("defp decode_query_param(\"true\"), do: true");
        writer.write("defp decode_query_param(\"false\"), do: false");
        writer.write("defp decode_query_param(value), do: value");
        writer.write("");
        emitDecodeJsonBodyHelper(writer);
    }

    private static void emitDecodeJsonBodyHelper(ElixirWriter writer) {
        writer.write("defp decode_json_body(\"\"), do: %{}");
        writer.write("defp decode_json_body(body) do");
        writer.indent();
        writer.write("case Jason.decode(body) do");
        writer.indent();
        writer.write("{:ok, map} when is_map(map) -> map");
        writer.write("_ -> %{}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static String buildElixirPathExpression(
            String uriTemplate, List<HttpBinding> labels, SymbolProvider sp) {
        if (labels.isEmpty()) {
            return "\"" + escapeElixirString(uriTemplate) + "\"";
        }
        Map<String, HttpBinding> byLocation = new HashMap<>();
        for (HttpBinding lb : labels) {
            byLocation.put(lb.getLocationName(), lb);
        }
        StringBuilder sb = new StringBuilder("\"");
        int pos = 0;
        while (pos < uriTemplate.length()) {
            int start = uriTemplate.indexOf('{', pos);
            if (start < 0) {
                sb.append(escapeElixirString(uriTemplate.substring(pos)));
                break;
            }
            if (start > pos) {
                sb.append(escapeElixirString(uriTemplate.substring(pos, start)));
            }
            int end = uriTemplate.indexOf('}', start);
            String labelName = uriTemplate.substring(start + 1, end);
            HttpBinding lb = byLocation.get(labelName);
            if (lb != null) {
                String field = fieldName(sp, lb.getMember());
                sb.append("#{uri_encode(input.").append(field).append(")}");
            } else {
                sb.append(escapeElixirString("{" + labelName + "}"));
            }
            pos = end + 1;
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String fieldName(SymbolProvider sp, MemberShape member) {
        Symbol sym = sp.toSymbol(member);
        return sym.getProperty("fieldName", String.class)
                .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
    }

    private static String escapeElixirString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
