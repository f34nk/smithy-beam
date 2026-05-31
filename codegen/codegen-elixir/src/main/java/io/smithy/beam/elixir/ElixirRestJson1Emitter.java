package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamNameUtils;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.EnumValueTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

import software.amazon.smithy.model.pattern.SmithyPattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST JSON 1 codec emitter for Elixir. Generates encode_request and decode_response
 * functions per operation using Jason for JSON and Req for HTTP.
 */
public final class ElixirRestJson1Emitter {

    private ElixirRestJson1Emitter() {}

    public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
        Model model = ctx.model();
        ShapeId protocol = ctx.resolvedProtocolTraitId();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

        String serverCodecModule =
                ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

        ctx.writerDelegator().useFileWriter(
                layout.serverCodecModuleName(protocol) + ".ex", writer -> {
            writer.write("defmodule $L do", serverCodecModule);
            writer.indent();
            writer.write("@moduledoc \"Server REST JSON 1 codecs for $L (generated). Do not edit.\"",
                    service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            writer.write("");

            for (OperationShape op : operations) {
                emitRequestDecoder(writer, model, op, httpIndex, sp, typesMod);
                emitResponseEncoder(writer, model, op, httpIndex, sp, typesMod);
            }

            emitEnumHelpers(writer, model, service, sp);
            emitUnionHelpers(writer, model, service, sp);
            emitHelpers(writer);

            writer.dedent();
            writer.write("end");
        });
    }

    public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
        Model model = ctx.model();
        ShapeId protocol = ctx.resolvedProtocolTraitId();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        String moduleName = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
        String codecFile = layout.clientCodecModuleName(protocol) + ".ex";
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
        boolean encodeWithConfig = serviceHasHostLabelOperations(model, service);

        ctx.writerDelegator().useFileWriter(codecFile, writer -> {
            writer.write("defmodule $L do", moduleName);
            writer.indent();
            writer.write("@moduledoc \"REST JSON 1 codecs for $L (generated). Do not edit.\"",
                    service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            writer.write("");

            for (OperationShape op : operations) {
                emitEncoder(writer, model, op, httpIndex, sp, typesMod, runtimeMod, encodeWithConfig);
                emitRequestDecoder(writer, model, op, httpIndex, sp, typesMod);
                emitDecoder(writer, model, op, httpIndex, sp, typesMod);
            }

            for (OperationShape op : operations) {
                emitErrorDispatch(writer, model, op, sp, typesMod);
            }

            emitEnumHelpers(writer, model, service, sp);
            emitUnionHelpers(writer, model, service, sp);
            emitHelpers(writer);
            if (encodeWithConfig) {
                emitBuildHostHelpers(writer, model, service, sp);
            }

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
            String runtimeMod,
            boolean encodeWithConfig) {

        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
        String method = httpTrait.getMethod();
        String uriTemplate = httpTrait.getUri().toString();

        BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
        boolean hasHostLabels = !hostLabelIndex.hostLabelMembers(op).isEmpty()
                && op.hasTrait(EndpointTrait.class);

        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> reqPayload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

        String requestContentType = resolvedRequestContentType(model, op);
        boolean streamingRequestPayload = hasStreamingRequestPayload(model, reqPayload, method);

        String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
        String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";

        if (encodeWithConfig) {
            writer.write("@spec encode_$L_request(map(), $L) :: $L", opName, inputType, httpRequestType);
            writer.write("def encode_$L_request(config, input) do", opName);
        } else {
            writer.write("@spec encode_$L_request($L) :: $L", opName, inputType, httpRequestType);
            writer.write("def encode_$L_request(input) do", opName);
        }
        writer.indent();

        for (MemberShape member : input.members()) {
            if (member.hasTrait(IdempotencyTokenTrait.class)) {
                String field = fieldName(sp, member);
                writer.write("input =");
                writer.indent();
                writer.write("case input.$L do", field);
                writer.indent();
                writer.write("nil -> %{input | $L: generate_uuid()}", field);
                writer.write("_ -> input");
                writer.dedent();
                writer.write("end");
                writer.dedent();
                writer.write("");
            }
        }

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

        if (!queryParams.isEmpty()) {
            for (HttpBinding qp : queryParams) {
                String field = fieldName(sp, qp.getMember());
                writer.write("query_extra =");
                writer.indent();
                writer.write("case input.$L do", field);
                writer.indent();
                writer.write("nil -> []");
                writer.write("m when is_map(m) -> Map.to_list(m)");
                writer.dedent();
                writer.write("end");
                writer.dedent();
                writer.write("");
                writer.write("query =");
                writer.indent();
                writer.write("query");
                writer.write("|> Map.to_list()");
                writer.write("|> Enum.concat(query_extra)");
                writer.write("|> Map.new()");
                writer.dedent();
            }
        }

        if (!headers.isEmpty()) {
            writer.write("extra_headers = [");
            for (HttpBinding hb : headers) {
                String field = fieldName(sp, hb.getMember());
                writer.write("  (if input.$L != nil, do: {\"$L\", to_string(input.$L)}, else: nil),",
                        field, hb.getLocationName(), field);
            }
            writer.write("] |> Enum.reject(&is_nil/1)");
            writer.write("headers = [{\"Content-Type\", \"$L\"} | extra_headers]", requestContentType);
        } else {
            writer.write("headers = [{\"Content-Type\", \"$L\"}]", requestContentType);
        }

        for (HttpBinding ph : prefixHeaders) {
            String field = fieldName(sp, ph.getMember());
            String prefix = ph.getLocationName();
            writer.write("headers = headers ++ prefix_headers_to_list(\"$L\", input.$L)", prefix, field);
        }

        boolean hasBody = !docMembers.isEmpty()
                && !method.equals("GET") && !method.equals("DELETE") && !method.equals("HEAD");
        if (streamingRequestPayload) {
            HttpBinding payload = reqPayload.get(0);
            String field = fieldName(sp, payload.getMember());
            writer.write("stream = input.$L", field);
            writer.write("body = \"\"");
        } else if (!reqPayload.isEmpty()
                && !method.equals("GET") && !method.equals("DELETE") && !method.equals("HEAD")) {
            HttpBinding payload = reqPayload.get(0);
            String field = fieldName(sp, payload.getMember());
            writer.write("body = input.$L || \"\"", field);
        } else if (hasBody) {
            writer.write("body_map = %{");
            for (HttpBinding db : docMembers) {
                String field = fieldName(sp, db.getMember());
                String wireKey = jsonKey(db.getMember());
                Shape target = model.expectShape(db.getMember().getTarget());
                if (target instanceof EnumShape || target instanceof IntEnumShape) {
                    String helperName = enumHelperName(target);
                    writer.write("  \"$L\" => encode_$L(input.$L),", wireKey, helperName, field);
                } else if (target instanceof UnionShape) {
                    String helperName = unionHelperName(target);
                    writer.write("  \"$L\" => encode_$L(input.$L),", wireKey, helperName, field);
                } else if (target instanceof TimestampShape) {
                    String encodeHelper = timestampEncodeHelper(
                            httpIndex, db.getMember(), HttpBinding.Location.DOCUMENT);
                    writer.write("  \"$L\" => $L(input.$L),", wireKey, encodeHelper, field);
                } else {
                    writer.write("  \"$L\" => input.$L,", wireKey, field);
                }
            }
            writer.write("}");
            writer.write("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
            writer.write("|> Map.new()");
            writer.write("body = Jason.encode!(body_map)");
        } else {
            writer.write("body = \"\"");
        }

        if (hasHostLabels) {
            writer.write("host = build_host(input, config)");
        }

        writer.write("%RuntimeTypes.HttpRequest{");
        writer.write("  method: \"$L\",", method);
        writer.write("  path: path,");
        writer.write("  query: query,");
        writer.write("  headers: headers,");
        writer.write("  body: body");
        if (streamingRequestPayload) {
            writer.write("  ,stream: stream");
        }
        if (hasHostLabels) {
            writer.write("  ,host: host");
        }
        writer.write("}");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitResponseEncoder(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod) {

        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputStruct = sp.toSymbol(output).getName();
        int statusCode = httpIndex.getResponseCode(op);

        List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> respPrefixHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

        String responseContentType = resolvedResponseContentType(model, op);

        String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));

        writer.write("@doc \"Encode response for $L.\"", op.getId());
        writer.write("@spec encode_$L_response($L) :: map()", opName, outputType);
        writer.write("def encode_$L_response(%Types.$L{} = output) do", opName, outputStruct);
        writer.indent();

        if (!respPayload.isEmpty()) {
            HttpBinding pb = respPayload.get(0);
            String field = fieldName(sp, pb.getMember());
            if (isStreamingBlob(model, pb.getMember())) {
                writer.write("stream = output.$L", field);
                writer.write("body = \"\"");
            } else {
                writer.write("body = output.$L", field);
            }
        } else if (!respDoc.isEmpty()) {
            writer.write("body_map = %{");
            for (HttpBinding db : respDoc) {
                String field = fieldName(sp, db.getMember());
                String wireKey = jsonKey(db.getMember());
                Shape target = model.expectShape(db.getMember().getTarget());
                if (target instanceof EnumShape || target instanceof IntEnumShape) {
                    String helperName = enumHelperName(target);
                    writer.write("  \"$L\" => encode_$L(output.$L),", wireKey, helperName, field);
                } else if (target instanceof UnionShape) {
                    String helperName = unionHelperName(target);
                    writer.write("  \"$L\" => encode_$L(output.$L),", wireKey, helperName, field);
                } else if (target instanceof TimestampShape) {
                    String encodeHelper = timestampEncodeHelper(
                            httpIndex, db.getMember(), HttpBinding.Location.DOCUMENT);
                    writer.write("  \"$L\" => $L(output.$L),", wireKey, encodeHelper, field);
                } else {
                    writer.write("  \"$L\" => output.$L,", wireKey, field);
                }
            }
            writer.write("}");
            writer.write("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
            writer.write("|> Map.new()");
            writer.write("body = Jason.encode!(body_map)");
        } else {
            writer.write("body = \"\"");
        }

        if (!respHeaders.isEmpty()) {
            writer.write("extra_headers = [");
            for (HttpBinding hb : respHeaders) {
                String field = fieldName(sp, hb.getMember());
                writer.write("  (if output.$L != nil, do: {\"$L\", to_string(output.$L)}, else: nil),",
                        field, hb.getLocationName(), field);
            }
            writer.write("] |> Enum.reject(&is_nil/1)");
            writer.write("headers = [{\"Content-Type\", \"$L\"} | extra_headers]", responseContentType);
        } else {
            writer.write("headers = [{\"Content-Type\", \"$L\"}]", responseContentType);
        }

        for (HttpBinding ph : respPrefixHeaders) {
            String field = fieldName(sp, ph.getMember());
            String prefix = ph.getLocationName();
            writer.write("headers = headers ++ prefix_headers_to_list(\"$L\", output.$L)", prefix, field);
        }

        writer.write("%{status: $L, headers: headers, body: body", statusCode);
        if (!respPayload.isEmpty() && isStreamingBlob(model, respPayload.get(0).getMember())) {
            writer.write(", stream: stream");
        }
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
        List<HttpBinding> queryParams = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> prefixHeaders = httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> docMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> reqPayload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
        boolean streamingRequestPayload = hasStreamingRequestPayload(model, reqPayload, null);

        if (labels.isEmpty()) {
            if (streamingRequestPayload) {
                writer.write(
                        "def decode_$L_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body, stream: stream}) do",
                        opName);
            } else {
                writer.write(
                        "def decode_$L_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}) do",
                        opName);
            }
        } else {
            if (streamingRequestPayload) {
                writer.write(
                        "def decode_$L_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body, stream: stream}, label_map) do",
                        opName);
            } else {
                writer.write(
                        "def decode_$L_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}, label_map) do",
                        opName);
            }
        }
        writer.indent();
        emitRequestDecoderStruct(
                writer, model, httpIndex, labels, queries, queryParams, headers, prefixHeaders, docMembers,
                reqPayload, sp, inputStruct);
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitRequestDecoderStruct(
            ElixirWriter writer,
            Model model,
            HttpBindingIndex httpIndex,
            List<HttpBinding> labels,
            List<HttpBinding> queries,
            List<HttpBinding> queryParams,
            List<HttpBinding> headers,
            List<HttpBinding> prefixHeaders,
            List<HttpBinding> docMembers,
            List<HttpBinding> reqPayload,
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
        for (HttpBinding qp : queryParams) {
            String field = fieldName(sp, qp.getMember());
            writer.write("  $L: query,", field);
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
        for (HttpBinding ph : prefixHeaders) {
            String field = fieldName(sp, ph.getMember());
            writer.write("  $L: prefix_headers_from_list(headers, \"$L\"),", field, ph.getLocationName());
        }
        for (HttpBinding db : docMembers) {
            String field = fieldName(sp, db.getMember());
            String wireKey = jsonKey(db.getMember());
            Shape target = model.expectShape(db.getMember().getTarget());
            if (target instanceof EnumShape || target instanceof IntEnumShape) {
                String helperName = enumHelperName(target);
                writer.write("  $L: decode_$L(Map.get(decoded, \"$L\")),", field, helperName, wireKey);
            } else if (target instanceof UnionShape) {
                String helperName = unionHelperName(target);
                writer.write("  $L: decode_$L(Map.get(decoded, \"$L\")),", field, helperName, wireKey);
            } else if (target instanceof TimestampShape) {
                String decodeHelper = timestampDecodeHelper(
                        httpIndex, db.getMember(), HttpBinding.Location.DOCUMENT);
                writer.write("  $L: $L(Map.get(decoded, \"$L\")),", field, decodeHelper, wireKey);
            } else {
                writer.write("  $L: $L,", field, documentDecodeExpr(wireKey, target));
            }
        }
        for (HttpBinding pb : reqPayload) {
            String field = fieldName(sp, pb.getMember());
            if (isStreamingBlob(model, pb.getMember())) {
                writer.write("  $L: stream,", field);
            } else {
                writer.write("  $L: body,", field);
            }
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
        List<HttpBinding> respPrefixHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
        List<HttpBinding> respDoc = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
        List<HttpBinding> respCode = httpIndex.getResponseBindings(op, HttpBinding.Location.RESPONSE_CODE);
        boolean streamingResponsePayload = !respPayload.isEmpty()
                && isStreamingBlob(model, respPayload.get(0).getMember());

        if (!respCode.isEmpty()) {
            if (streamingResponsePayload) {
                writer.write(
                        "def decode_$L_response(%RuntimeTypes.HttpResponse{status: http_status, headers: headers,"
                                + " body: body, stream: stream}) when http_status >= 200 and http_status < 300 do",
                        opName);
            } else {
                writer.write(
                        "def decode_$L_response(%RuntimeTypes.HttpResponse{status: http_status, headers: headers,"
                                + " body: body}) when http_status >= 200 and http_status < 300 do",
                        opName);
            }
        } else if (streamingResponsePayload) {
            writer.write(
                    "def decode_$L_response(%RuntimeTypes.HttpResponse{status: $L, headers: headers, body: body, stream: stream}) do",
                    opName, successCode);
        } else {
            writer.write(
                    "def decode_$L_response(%RuntimeTypes.HttpResponse{status: $L, headers: headers, body: body}) do",
                    opName, successCode);
        }
        writer.indent();

        boolean needsContentTypeCheck = responsePayloadRequiresContentTypeCheck(model, respPayload);
        if (needsContentTypeCheck) {
            String expectedContentType = resolvedResponseContentType(model, op);
            writer.write("with :ok <- content_type_matches(headers, \"$L\") do", expectedContentType);
            writer.indent();
        }

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
        for (HttpBinding ph : respPrefixHeaders) {
            String field = fieldName(sp, ph.getMember());
            writer.write("  $L: prefix_headers_from_list(headers, \"$L\"),", field, ph.getLocationName());
        }
        for (HttpBinding db : respDoc) {
            String field = fieldName(sp, db.getMember());
            String wireKey = jsonKey(db.getMember());
            Shape target = model.expectShape(db.getMember().getTarget());
            if (target instanceof EnumShape || target instanceof IntEnumShape) {
                String helperName = enumHelperName(target);
                writer.write("  $L: decode_$L(Map.get(decoded, \"$L\")),", field, helperName, wireKey);
            } else if (target instanceof UnionShape) {
                String helperName = unionHelperName(target);
                writer.write("  $L: decode_$L(Map.get(decoded, \"$L\")),", field, helperName, wireKey);
            } else if (target instanceof TimestampShape) {
                String decodeHelper = timestampDecodeHelper(
                        httpIndex, db.getMember(), HttpBinding.Location.DOCUMENT);
                writer.write("  $L: $L(Map.get(decoded, \"$L\")),", field, decodeHelper, wireKey);
            } else {
                writer.write("  $L: $L,", field, documentDecodeExpr(wireKey, target));
            }
        }
        for (HttpBinding pb : respPayload) {
            String field = fieldName(sp, pb.getMember());
            if (isStreamingBlob(model, pb.getMember())) {
                writer.write("  $L: stream,", field);
            } else {
                writer.write("  $L: body,", field);
            }
        }
        for (HttpBinding rcb : respCode) {
            String field = fieldName(sp, rcb.getMember());
            writer.write("  $L: http_status,", field);
        }
        if (needsContentTypeCheck) {
            writer.write("}}");
            writer.dedent();
            writer.write("end");
        } else {
            writer.write("}}");
        }

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

    private static void emitEnumHelpers(
            ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {

        Set<ShapeId> emitted = new LinkedHashSet<>();
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);

        for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
            for (HttpBinding.Location loc : HttpBinding.Location.values()) {
                for (HttpBinding b : httpIndex.getRequestBindings(op, loc)) {
                    collectEnumTarget(model, b.getMember(), emitted);
                }
                for (HttpBinding b : httpIndex.getResponseBindings(op, loc)) {
                    collectEnumTarget(model, b.getMember(), emitted);
                }
            }
        }

        for (ShapeId enumId : emitted) {
            Shape enumShape = model.expectShape(enumId);
            if (enumShape instanceof EnumShape) {
                emitElixirEnumHelpers(writer, (EnumShape) enumShape, sp);
            } else if (enumShape instanceof IntEnumShape) {
                emitElixirIntEnumHelpers(writer, (IntEnumShape) enumShape, sp);
            }
        }
    }

    private static void collectEnumTarget(Model model, MemberShape member, Set<ShapeId> out) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof EnumShape || target instanceof IntEnumShape) {
            out.add(target.getId());
        }
    }

    private static void emitUnionHelpers(
            ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {

        Set<ShapeId> emitted = new LinkedHashSet<>();
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);

        for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
            for (HttpBinding.Location loc : HttpBinding.Location.values()) {
                for (HttpBinding b : httpIndex.getRequestBindings(op, loc)) {
                    collectUnionTarget(model, b.getMember(), emitted);
                }
                for (HttpBinding b : httpIndex.getResponseBindings(op, loc)) {
                    collectUnionTarget(model, b.getMember(), emitted);
                }
            }
        }

        for (ShapeId unionId : emitted) {
            UnionShape union = model.expectShape(unionId, UnionShape.class);
            emitElixirUnionHelpers(writer, union, sp);
        }
    }

    private static void collectUnionTarget(Model model, MemberShape member, Set<ShapeId> out) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof UnionShape) {
            out.add(target.getId());
        }
    }

    private static void emitElixirUnionHelpers(ElixirWriter writer, UnionShape shape, SymbolProvider sp) {
        String helperName = unionHelperName(shape);
        writer.write("# Union helpers for $L", shape.getId());
        writer.write("defp decode_$L(map) when is_map(map) do", helperName);
        writer.indent();
        writer.write("case Map.to_list(map) do");
        writer.indent();
        for (MemberShape m : shape.members()) {
            String wireKey = m.getMemberName();
            String tag = unionTagForMember(sp, m);
            writer.write("[{\"$L\", v}] -> {$L, v}", wireKey, tag);
        }
        writer.write("[{k, _v}] -> {:unknown, k}");
        writer.write("_ -> nil");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("defp decode_$L(nil), do: nil", helperName);
        writer.write("");

        for (MemberShape m : shape.members()) {
            String wireKey = m.getMemberName();
            String tag = unionTagForMember(sp, m);
            writer.write("defp encode_$L({$L, v}), do: %{\"$L\" => v}", helperName, tag, wireKey);
        }
        writer.write("defp encode_$L({:unknown, k}) when is_binary(k), do: %{k => nil}", helperName);
        writer.write("defp encode_$L(nil), do: nil", helperName);
        writer.write("");
    }

    private static String unionHelperName(Shape shape) {
        return BeamNameUtils.toSnakeCase(shape.getId().getName());
    }

    private static String unionTagForMember(SymbolProvider sp, MemberShape member) {
        return ":" + sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
    }

    private static void emitElixirEnumHelpers(ElixirWriter writer, EnumShape shape, SymbolProvider sp) {
        String helperName = enumHelperName(shape);
        writer.write("# Enum helpers for $L", shape.getId());
        for (MemberShape m : shape.members()) {
            String wireValue = m.getTrait(EnumValueTrait.class)
                    .flatMap(EnumValueTrait::getStringValue)
                    .orElse(m.getMemberName());
            String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
            writer.write("defp decode_$L(\"$L\"), do: $L", helperName, wireValue, atom);
        }
        writer.write("defp decode_$L(v) when is_binary(v), do: {:unknown, v}", helperName);
        writer.write("defp decode_$L(nil), do: nil", helperName);
        writer.write("");
        for (MemberShape m : shape.members()) {
            String wireValue = m.getTrait(EnumValueTrait.class)
                    .flatMap(EnumValueTrait::getStringValue)
                    .orElse(m.getMemberName());
            String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
            writer.write("defp encode_$L($L), do: \"$L\"", helperName, atom, wireValue);
        }
        writer.write("defp encode_$L({:unknown, v}) when is_binary(v), do: v", helperName);
        writer.write("defp encode_$L(nil), do: nil", helperName);
        writer.write("");
    }

    private static void emitElixirIntEnumHelpers(ElixirWriter writer, IntEnumShape shape, SymbolProvider sp) {
        String helperName = enumHelperName(shape);
        writer.write("# IntEnum helpers for $L", shape.getId());
        for (MemberShape m : shape.members()) {
            int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
            String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
            writer.write("defp decode_$L($L), do: $L", helperName, wireValue, atom);
        }
        writer.write("defp decode_$L(v) when is_integer(v), do: {:unknown, v}", helperName);
        writer.write("defp decode_$L(nil), do: nil", helperName);
        writer.write("");
        for (MemberShape m : shape.members()) {
            int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
            String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
            writer.write("defp encode_$L($L), do: $L", helperName, atom, wireValue);
        }
        writer.write("defp encode_$L({:unknown, v}) when is_integer(v), do: v", helperName);
        writer.write("defp encode_$L(nil), do: nil", helperName);
        writer.write("");
    }

    private static String enumHelperName(Shape shape) {
        return BeamNameUtils.toSnakeCase(shape.getId().getName());
    }

    private static String enumAtomForMember(SymbolProvider sp, Shape enumShape, String memberName) {
        @SuppressWarnings("unchecked")
        Map<String, String> byMember = sp.toSymbol(enumShape)
                .getProperty("enumAtomByMember", Map.class)
                .orElseThrow();
        return byMember.get(memberName);
    }

    static void emitSharedCodecHelpers(
            ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {
        emitEnumHelpers(writer, model, service, sp);
        emitUnionHelpers(writer, model, service, sp);
        emitHelpers(writer);
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
        writer.write("defp prefix_headers_to_list(_prefix, nil), do: []");
        writer.write("defp prefix_headers_to_list(prefix, map) when is_map(map) do");
        writer.indent();
        writer.write("Enum.map(map, fn {k, v} -> {prefix <> k, to_string(v)} end)");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp prefix_headers_from_list(headers, prefix) do");
        writer.indent();
        writer.write("headers");
        writer.write("|> Enum.filter(fn {name, _} -> String.starts_with?(name, prefix) end)");
        writer.write("|> Map.new(fn {name, val} -> {String.slice(name, byte_size(prefix)..-1//1), val} end)");
        writer.write("|> case do");
        writer.indent();
        writer.write("map when map == %{} -> nil");
        writer.write("map -> map");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp decode_sparse_list(nil), do: nil");
        writer.write("defp decode_sparse_list(list) when is_list(list),");
        writer.write("    do: Enum.map(list, fn nil -> nil; v -> v end)");
        writer.write("");
        writer.write("defp decode_list(nil), do: nil");
        writer.write("defp decode_list(list) when is_list(list),");
        writer.write("    do: Enum.reject(list, &is_nil/1)");
        writer.write("");
        writer.write("defp decode_sparse_map(nil), do: nil");
        writer.write("defp decode_sparse_map(map) when is_map(map),");
        writer.write("    do: Map.new(map, fn {k, nil} -> {k, nil}; {k, v} -> {k, v} end)");
        writer.write("");
        writer.write("defp encode_timestamp_epoch_seconds(nil), do: nil");
        writer.write("defp encode_timestamp_epoch_seconds(%DateTime{} = dt),");
        writer.write("    do: DateTime.to_unix(dt)");
        writer.write("");
        writer.write("defp encode_timestamp_date_time(nil), do: nil");
        writer.write("defp encode_timestamp_date_time(%DateTime{} = dt),");
        writer.write("    do: DateTime.to_iso8601(dt)");
        writer.write("");
        writer.write("defp decode_timestamp_epoch_seconds(nil), do: nil");
        writer.write("defp decode_timestamp_epoch_seconds(v) when is_number(v),");
        writer.write("    do: DateTime.from_unix!(trunc(v))");
        writer.write("");
        writer.write("defp decode_timestamp_date_time(nil), do: nil");
        writer.write("defp decode_timestamp_date_time(v) when is_binary(v) do");
        writer.indent();
        writer.write("case DateTime.from_iso8601(v) do");
        writer.indent();
        writer.write("{:ok, dt, _} -> dt");
        writer.write("_ -> nil");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp generate_uuid do");
        writer.indent();
        writer.write("<<a::32, b::16, _::4, c::12, _::2, d::14, e::48>> = :crypto.strong_rand_bytes(16)");
        writer.write("<<a::32, b::16, 4::4, c::12, 2::2, d::14, e::48>>");
        writer.write("|> Base.encode16(case: :lower)");
        writer.write("|> then(fn hex ->");
        writer.indent();
        writer.write("<<part_a::8, part_b::4, part_c::4, part_d::4, part_e::12>> = hex");
        writer.write("\"#{part_a}-#{part_b}-#{part_c}-#{part_d}-#{part_e}\"");
        writer.dedent();
        writer.write("end)");
        writer.dedent();
        writer.write("end");
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
        writer.write("defp content_type_matches(headers, expected) do");
        writer.indent();
        writer.write("case List.keyfind(headers, \"Content-Type\", 0) do");
        writer.indent();
        writer.write("{_, ct} when ct == expected -> :ok");
        writer.write("{_, ct} when is_binary(ct) ->");
        writer.indent();
        writer.write("if ct_base(ct) == ct_base(expected), do: :ok, else: {:error, {:invalid_content_type, ct}}");
        writer.dedent();
        writer.write("_ -> {:error, {:invalid_content_type, nil}}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp ct_base(ct) do");
        writer.indent();
        writer.write("case String.split(ct, \";\") do");
        writer.indent();
        writer.write("[base | _] -> base");
        writer.write("_ -> ct");
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

    private static String timestampEncodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        TimestampFormatTrait.Format fmt = httpIndex.determineTimestampFormat(
                member, location, TimestampFormatTrait.Format.DATE_TIME);
        return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "encode_timestamp_epoch_seconds"
                : "encode_timestamp_date_time";
    }

    private static String timestampDecodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        TimestampFormatTrait.Format fmt = httpIndex.determineTimestampFormat(
                member, location, TimestampFormatTrait.Format.DATE_TIME);
        return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "decode_timestamp_epoch_seconds"
                : "decode_timestamp_date_time";
    }

    private static String documentDecodeExpr(String jsonKey, Shape target) {
        String raw = "Map.get(decoded, \"" + jsonKey + "\")";
        if (target instanceof ListShape) {
            String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
            return helper + "(" + raw + ")";
        }
        if (target instanceof MapShape) {
            if (target.hasTrait(SparseTrait.class)) {
                return "decode_sparse_map(" + raw + ")";
            }
            return raw;
        }
        return raw;
    }

    private static String fieldName(SymbolProvider sp, MemberShape member) {
        Symbol sym = sp.toSymbol(member);
        return sym.getProperty("fieldName", String.class)
                .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
    }

    private static String jsonKey(MemberShape member) {
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }

    private static String escapeElixirString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void emitBuildHostHelpers(
            ElixirWriter writer,
            Model model,
            ServiceShape service,
            SymbolProvider sp) {

        writer.write("defp split_base_url(\"\"), do: {\"\", \"\"}");
        writer.write("defp split_base_url(base_url) do");
        writer.indent();
        writer.write("case URI.parse(base_url) do");
        writer.indent();
        writer.write("%URI{scheme: scheme, host: host} = uri when is_binary(host) ->");
        writer.indent();
        writer.write("port_suffix =");
        writer.indent();
        writer.write("case {uri.scheme, uri.port} do");
        writer.indent();
        writer.write("{\"https\", 443} -> \"\"");
        writer.write("{\"http\", 80} -> \"\"");
        writer.write("{_, nil} -> \"\"");
        writer.write("{_, port} -> \":#{port}\"");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("{scheme <> \"://\", host <> port_suffix}");
        writer.dedent();
        writer.write("_ ->");
        writer.indent();
        writer.write("{\"\", base_url}");
        writer.dedent();
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");

        BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
        for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
            List<MemberShape> hostLabels = hostLabelIndex.hostLabelMembers(op);
            if (hostLabels.isEmpty() || !op.hasTrait(EndpointTrait.class)) {
                continue;
            }
            StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
            String inputStruct = sp.toSymbol(input).getName();
            SmithyPattern hostPrefix = op.expectTrait(EndpointTrait.class).getHostPrefix();
            String prefixExpr = buildElixirHostPrefixExpression(hostPrefix, hostLabels, sp);

            writer.write("defp build_host(%Types.$L{", inputStruct);
            for (int i = 0; i < hostLabels.size(); i++) {
                MemberShape member = hostLabels.get(i);
                String field = fieldName(sp, member);
                String comma = i < hostLabels.size() - 1 ? "," : "";
                writer.write("  $L: $L$L", field, field, comma);
            }
            writer.write("}, config) do");
            writer.indent();
            writer.write("base_url = Map.get(config, :base_url, \"\")");
            writer.write("{_scheme, authority} = split_base_url(base_url)");
            writer.write("prefix = $L", prefixExpr);
            writer.write("prefix <> authority");
            writer.dedent();
            writer.write("end");
            writer.write("");
        }
    }

    public static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
        BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
        for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
            if (!hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class)) {
                return true;
            }
        }
        return false;
    }

    private static String buildElixirHostPrefixExpression(
            SmithyPattern hostPrefix, List<MemberShape> hostLabels, SymbolProvider sp) {
        if (hostPrefix.getSegments().isEmpty()) {
            return "\"\"";
        }
        Map<String, String> labelFields = new HashMap<>();
        for (MemberShape member : hostLabels) {
            labelFields.put(member.getMemberName(), fieldName(sp, member));
        }
        StringBuilder sb = new StringBuilder();
        List<SmithyPattern.Segment> segments = hostPrefix.getSegments();
        for (int i = 0; i < segments.size(); i++) {
            SmithyPattern.Segment segment = segments.get(i);
            if (segment.isLabel()) {
                String field = labelFields.getOrDefault(
                        segment.getContent(), BeamNameUtils.toSnakeCase(segment.getContent()));
                sb.append("URI.encode(to_string(").append(field).append("))");
            } else {
                sb.append("\"").append(escapeElixirString(segment.getContent())).append("\"");
            }
            if (i < segments.size() - 1) {
                sb.append(" <> ");
            }
        }
        return sb.toString();
    }

    private static String resolvedRequestContentType(Model model, OperationShape op) {
        return BeamHttpBindings.from(model)
                .requestContentType(op, "application/json")
                .orElse("application/json");
    }

    private static String resolvedResponseContentType(Model model, OperationShape op) {
        return BeamHttpBindings.from(model)
                .responseContentType(op, "application/json")
                .orElse("application/json");
    }

    private static boolean responsePayloadRequiresContentTypeCheck(
            Model model, List<HttpBinding> respPayload) {
        if (respPayload.isEmpty()) {
            return false;
        }
        Shape target = model.expectShape(respPayload.get(0).getMember().getTarget());
        return target.hasTrait(MediaTypeTrait.class);
    }

    private static boolean hasStreamingRequestPayload(
            Model model, List<HttpBinding> reqPayload, String method) {
        if (reqPayload.isEmpty()) {
            return false;
        }
        if (method != null
                && (method.equals("GET") || method.equals("DELETE") || method.equals("HEAD"))) {
            return false;
        }
        return isStreamingBlob(model, reqPayload.get(0).getMember());
    }

    private static boolean isStreamingBlob(Model model, MemberShape member) {
        Shape target = model.expectShape(member.getTarget());
        return target instanceof BlobShape && target.hasTrait(StreamingTrait.class);
    }
}
