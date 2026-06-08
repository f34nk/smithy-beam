package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamRestXmlProtocolCodegen;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST-XML protocol codec emitter for Elixir clients and servers.
 */
public final class ElixirRestXmlEmitter {

    private static final String DEFAULT_CONTENT_TYPE = "application/xml";

    private ElixirRestXmlEmitter() {}

    private static void emitRejectNilMapPipeline(
            ElixirWriter writer, String varName, Runnable emitMapEntries) {
        ElixirFormat.beginPipelineBinding(writer, varName);
        writer.write("%{");
        writer.indent();
        emitMapEntries.run();
        writer.dedent();
        writer.write("}");
        ElixirFormat.writePipelineStep(writer, "Enum.reject(fn {_, v} -> is_nil(v) end)");
        ElixirFormat.writePipelineStep(writer, "Map.new()");
        ElixirFormat.endPipelineBinding(writer);
    }

    private static void emitExtraHeadersPipeline(
            ElixirWriter writer,
            SymbolProvider sp,
            String recordVar,
            List<HttpBinding> headers) {
        ElixirFormat.beginPipelineBinding(writer, "extra_headers");
        writer.write("[");
        writer.indent();
        for (HttpBinding hb : headers) {
            String field = fieldName(sp, hb.getMember());
            ElixirFormat.writeIfInList(
                    writer,
                    recordVar + "." + field + " != nil",
                    "{\"" + hb.getLocationName() + "\", to_string(" + recordVar + "." + field + ")}");
        }
        writer.dedent();
        writer.write("]");
        ElixirFormat.writePipelineStep(writer, "Enum.reject(&is_nil/1)");
        ElixirFormat.endPipelineBinding(writer);
    }

    public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        String moduleName = ElixirSymbolProvider.toModuleName(
                layout.clientCodecModuleName(BeamRestXmlProtocolCodegen.REST_XML));
        String codecFile = layout.clientCodecModuleName(BeamRestXmlProtocolCodegen.REST_XML) + ".ex";
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
        boolean encodeWithConfig = serviceEncodesWithConfig(model, service);
        boolean checksumBindings = ElixirHttpChecksumEmitter.serviceHasChecksumOperations(model, service);

        ctx.writerDelegator().useFileWriter(codecFile, writer -> {
            writer.write("defmodule $L do", moduleName);
            writer.indent();
            writer.write("@moduledoc \"REST-XML codecs for $L (generated). Do not edit.\"", service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            if (BeamS3CustomizationIndex.isS3Service(service)) {
                writer.write("alias S3Endpoint");
            }
            writer.write("");

            emitServiceXmlNamespace(writer, BeamXmlBindingIndex.xmlNamespaceUri(service));

            for (OperationShape op : operations) {
                emitEncoder(writer, model, service, op, httpIndex, sp, typesMod, runtimeMod, encodeWithConfig);
                emitDecoder(writer, model, op, httpIndex, sp, typesMod);
            }

            emitXmlHelpers(writer, checksumBindings);

            writer.dedent();
            writer.write("end");
        });
    }

    public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        String serverCodecModule = ElixirSymbolProvider.toModuleName(
                layout.serverCodecModuleName(BeamRestXmlProtocolCodegen.REST_XML));
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
        boolean checksumBindings = ElixirHttpChecksumEmitter.serviceHasChecksumOperations(model, service);

        ctx.writerDelegator().useFileWriter(
                layout.serverCodecModuleName(BeamRestXmlProtocolCodegen.REST_XML) + ".ex", writer -> {
            writer.write("defmodule $L do", serverCodecModule);
            writer.indent();
            writer.write("@moduledoc \"Server REST-XML codecs for $L (generated). Do not edit.\"",
                    service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            writer.write("");

            emitServiceXmlNamespace(writer, BeamXmlBindingIndex.xmlNamespaceUri(service));

            for (OperationShape op : operations) {
                emitRequestDecoder(writer, model, op, httpIndex, sp, typesMod);
                emitResponseEncoder(writer, model, op, httpIndex, sp, typesMod);
            }

            emitXmlHelpers(writer, checksumBindings);

            writer.dedent();
            writer.write("end");
        });
    }

    private static void emitServiceXmlNamespace(ElixirWriter writer, Optional<String> namespaceUri) {
        if (namespaceUri.isPresent()) {
            writer.write("defp xml_namespace, do: %{uri: \"$L\"}", namespaceUri.get());
        } else {
            writer.write("defp xml_namespace, do: %{}");
        }
        writer.write("");
    }

    private static void emitEncoder(
            ElixirWriter writer,
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod,
            String runtimeMod,
            boolean encodeWithConfig) {

        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
        String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
        String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";

        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
        List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
        List<HttpBinding> payloadMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

        if (encodeWithConfig) {
            ElixirFormat.writeSpec(
                    writer, "@spec", "encode_" + opName + "_request", "map(), " + inputType, httpRequestType);
            writer.write("def encode_$L_request(config, input) do", opName);
        } else {
            ElixirFormat.writeSpec(writer, "@spec", "encode_" + opName + "_request", inputType, httpRequestType);
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

        BeamS3CustomizationIndex s3Index = BeamS3CustomizationIndex.of(model);
        boolean s3BucketAddressing =
                s3Index.isS3Service(service) && s3Index.bucketLabelBinding(op).isPresent();
        boolean setHost = s3BucketAddressing;

        if (s3BucketAddressing) {
            String bucketField = fieldName(sp, s3Index.bucketLabelBinding(op).orElseThrow().getMember());
            String keyExpr = s3Index.keyLabelBinding(op)
                    .map(binding -> "to_string(input." + fieldName(sp, binding.getMember()) + ")")
                    .orElse("\"\"");
            if (encodeWithConfig) {
                writer.write("{host, path} = S3Endpoint.resolve_bucket_url(config, to_string(input.$L), $L)",
                        bucketField, keyExpr);
            } else {
                writer.write("{host, path} = S3Endpoint.resolve_bucket_url(%{}, to_string(input.$L), $L)",
                        bucketField, keyExpr);
            }
        } else {
            String pathExpr = buildPathExpression(httpTrait.getUri().toString(), labels, sp);
            writer.write("path = $L", pathExpr);
        }

        if (!queries.isEmpty()) {
            emitRejectNilMapPipeline(writer, "query", () -> {
                for (HttpBinding qb : queries) {
                    writer.write("\"$L\" => input.$L,", qb.getLocationName(), fieldName(sp, qb.getMember()));
                }
            });
        } else {
            writer.write("query = %{}");
        }

        if (headers.isEmpty()) {
            writer.write("headers = [{\"Content-Type\", \"application/xml\"}]");
        } else {
            emitExtraHeadersPipeline(writer, sp, "input", headers);
            writer.write("headers = [{\"Content-Type\", \"application/xml\"} | extra_headers]");
        }

        emitRequestBody(writer, model, payloadMembers, httpTrait.getMethod(), sp);
        ElixirHttpChecksumEmitter.emitRequestChecksumHeaders(writer, model, op, sp);

        writer.write("%RuntimeTypes.HttpRequest{");
        writer.write("  method: \"$L\",", httpTrait.getMethod());
        writer.write("  path: path,");
        writer.write("  query: query,");
        writer.write("  headers: headers,");
        writer.write("  body: body");
        if (setHost) {
            writer.write("  ,host: host");
        }
        writer.write("}");

        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitRequestBody(
            ElixirWriter writer,
            Model model,
            List<HttpBinding> payloadMembers,
            String method,
            SymbolProvider sp) {

        if (payloadMembers.isEmpty()
                || method.equals("GET")
                || method.equals("DELETE")
                || method.equals("HEAD")) {
            writer.write("body = \"\"");
            return;
        }

        HttpBinding payload = payloadMembers.get(0);
        MemberShape member = payload.getMember();
        Shape target = model.expectShape(member.getTarget());
        String field = fieldName(sp, member);

        if (target instanceof BlobShape || target instanceof StringShape) {
            writer.write("body =");
            writer.indent();
            writer.write("case input.$L do", field);
            writer.indent();
            writer.write("nil -> \"\"");
            writer.write("");
            writer.write("value -> value");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            return;
        }

        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        writer.write("body =");
        writer.indent();
        writer.write("case input.$L do", field);
        writer.indent();
        writer.write("nil -> \"\"");
        writer.write("payload_value ->");
        writer.indent();
        if (target instanceof StructureShape structure) {
            writer.write("member_map = $L", buildStructureMap(model, structure, "payload_value", sp));
            writer.write("encode_xml(%{\"$L\" => member_map}, xml_namespace())", rootElement);
        } else {
            writer.write("encode_xml(%{\"$L\" => payload_value}, xml_namespace())", rootElement);
        }
        writer.dedent();
        writer.dedent();
        writer.write("end");
        writer.dedent();
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
        String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
        int successCode = httpIndex.getResponseCode(op);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

        ElixirFormat.writeSpec(
                writer,
                "@spec",
                "decode_" + opName + "_response",
                "map()",
                "{:ok, " + outputType + "} | {:error, term()}");
        writer.write("def decode_$L_response(%{status: $L, headers: headers, body: body}) do", opName, successCode);
        writer.indent();

        if (!respPayload.isEmpty()) {
            HttpBinding pb = respPayload.get(0);
            MemberShape member = pb.getMember();
            Shape target = model.expectShape(member.getTarget());
            String field = fieldName(sp, member);
            if (target instanceof BlobShape || target instanceof StringShape) {
                ElixirHttpChecksumEmitter.emitResponseChecksumGuard(
                        writer, model, op, "{:ok, %Types." + structName(sp, output) + "{" + field + ": body}}");
            } else {
                String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
                writer.write("case parse_xml_root(body, \"$L\") do", rootElement);
                writer.indent();
                writer.write("{:ok, root} -> {:ok, %Types.$L{$L: $L}}",
                        structName(sp, output), field, decodeStructure(model, (StructureShape) target, "root", sp));
                writer.write("");
                writer.write("{:error, reason} -> {:error, reason}");
                writer.dedent();
                writer.write("end");
            }
        } else if (!output.members().isEmpty()) {
            String rootElement = BeamXmlBindingIndex.shapeElementName(output);
            writer.write("case parse_xml_root(body, \"$L\") do", rootElement);
            writer.indent();
            writer.write("{:ok, root} -> {:ok, $L}", decodeOutputStruct(model, output, "root", sp));
            writer.write("");
            writer.write("{:error, reason} -> {:error, reason}");
            writer.dedent();
            writer.write("end");
        } else {
            writer.write("{:ok, %Types.$L{}}", structName(sp, output));
        }

        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("def decode_$L_response(%{status: status, body: body}) do", opName);
        writer.indent();
        writer.write("{:error, {:unknown_error, status, body}}");
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
        String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> payloadMembers = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

        if (labels.isEmpty()) {
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "decode_" + opName + "_request",
                    "map()",
                    "{:ok, " + inputType + "} | {:error, term()}");
            writer.write("def decode_$L_request(%{body: body} = req) do", opName);
        } else {
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "decode_" + opName + "_request",
                    "map(), map()",
                    "{:ok, " + inputType + "} | {:error, term()}");
            writer.write("def decode_$L_request(labels, %{body: body} = _req) do", opName);
        }
        writer.indent();

        List<String> fields = new ArrayList<>();
        for (HttpBinding lb : labels) {
            String field = fieldName(sp, lb.getMember());
            fields.add(field + ": Map.get(labels, \"" + lb.getLocationName() + "\")");
        }

        if (!payloadMembers.isEmpty()) {
            HttpBinding payload = payloadMembers.get(0);
            MemberShape member = payload.getMember();
            Shape target = model.expectShape(member.getTarget());
            String field = fieldName(sp, member);
            if (target instanceof BlobShape || target instanceof StringShape) {
                fields.add(field + ": body");
            } else {
                String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
                fields.add(field + ": decode_payload(body, \"" + rootElement + "\")");
            }
        }

        writer.write("{:ok, %Types.$L{$L}}", structName(sp, input), String.join(", ", fields));
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
        String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
        int successCode = httpIndex.getResponseCode(op);
        List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

        ElixirFormat.writeSpec(writer, "@spec", "encode_" + opName + "_response", outputType, "map()");
        writer.write("def encode_$L_response(output) do", opName);
        writer.indent();
        writer.write("headers = [{\"Content-Type\", \"application/xml\"}]");

        if (!respPayload.isEmpty()) {
            HttpBinding pb = respPayload.get(0);
            MemberShape member = pb.getMember();
            Shape target = model.expectShape(member.getTarget());
            String field = fieldName(sp, member);
            if (target instanceof BlobShape || target instanceof StringShape) {
                writer.write("body = Map.get(output, :$L) || \"\"", field);
            } else {
                String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
                writer.write("body =");
                writer.indent();
                writer.write("case Map.get(output, :$L) do", field);
                writer.indent();
                writer.write("nil -> \"\"");
                writer.write("payload_value ->");
                writer.indent();
                if (target instanceof StructureShape structure) {
                    writer.write("member_map = $L", buildStructureMapFromOutput(model, structure, "payload_value", sp));
                    writer.write("encode_xml(%{\"$L\" => member_map}, xml_namespace())", rootElement);
                } else {
                    writer.write("encode_xml(%{\"$L\" => payload_value}, xml_namespace())", rootElement);
                }
                writer.dedent();
                writer.dedent();
                writer.write("end");
                writer.dedent();
            }
        } else if (!output.members().isEmpty()) {
            String rootElement = BeamXmlBindingIndex.shapeElementName(output);
            writer.write("member_map = %{");
            for (MemberShape member : output.members()) {
                String field = fieldName(sp, member);
                String wireName = BeamXmlBindingIndex.memberElementName(member);
                writer.write("  \"$L\" => output.$L,", wireName, field);
            }
            writer.write("}");
            writer.write("body = encode_xml(%{\"$L\" => member_map}, xml_namespace())", rootElement);
        } else {
            writer.write("body = \"\"");
        }

        writer.write("%RuntimeTypes.HttpResponse{status: $L, headers: headers, body: body}", successCode);
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitXmlHelpers(ElixirWriter writer, boolean checksumBindings) {
        writer.write("defp encode_xml(root_map, xml_ns) do");
        writer.indent();
        writer.write("[{root_name, content}] = Map.to_list(root_map)");
        writer.write("element = build_xml_element(root_name, content, xml_ns)");
        writer.write(":xmerl.export_simple([element], :xmerl_xmlns, [], [{:prolog, false}]) |> :erlang.iolist_to_binary()");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp build_xml_element(name, content, xml_ns) when is_map(content) do");
        writer.indent();
        writer.write("attrs = xml_namespace_attrs(xml_ns)");
        writer.write("children = for {k, v} <- content, v != nil, do: build_xml_child(k, v)");
        writer.write("{name, attrs, children}");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp build_xml_element(name, content, xml_ns) do");
        writer.indent();
        writer.write("{name, xml_namespace_attrs(xml_ns), [{:text, xml_text(content)}]}");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp build_xml_child(name, value) when is_map(value) do");
        writer.indent();
        writer.write("children = for {k, v} <- value, v != nil, do: build_xml_element(k, v, %{})");
        writer.write("{name, [], children}");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp build_xml_child(name, value), do: {name, [], [{:text, xml_text(value)}]}");
        writer.write("");
        writer.write("defp xml_namespace_attrs(%{uri: uri}), do: [xmlns: uri]");
        writer.write("defp xml_namespace_attrs(_), do: []");
        writer.write("");
        writer.write("defp parse_xml_root(body, root_name) do");
        writer.indent();
        writer.write("try do");
        writer.indent();
        writer.write("{xml, _} = :xmerl_scan.string(String.to_charlist(body))");
        writer.write("cond do");
        writer.indent();
        writer.write("xml_element_named(xml, root_name) -> {:ok, xml}");
        writer.write("true ->");
        writer.indent();
        writer.write("case find_element(root_name, element_content(xml)) do");
        writer.indent();
        writer.write("nil -> {:error, {:missing_root, root_name}}");
        writer.write("");
        writer.write("root -> {:ok, root}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("rescue");
        writer.indent();
        writer.write("reason -> {:error, {:xml_parse_error, reason}}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp decode_payload(body, root_name) do");
        writer.indent();
        writer.write("case parse_xml_root(body, root_name) do");
        writer.indent();
        writer.write("{:ok, _root} -> nil");
        writer.write("{:error, _} -> nil");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp xml_element_named(element, name), do: is_element(element) and element_name(element) == name");
        writer.write("");
        writer.write("defp element_content({:xmlElement, _, _, _, _, _, _, _, content, _, _, _}), do: content");
        writer.write("defp element_content({_, _, content, _, _, _}) when is_list(content), do: content");
        writer.write("defp element_content([h | _]), do: element_content(h)");
        writer.write("defp element_content(_), do: []");
        writer.write("");
        writer.write("defp find_element(name, content) do");
        writer.indent();
        writer.write("Enum.find_value(content, fn item ->");
        writer.indent();
        writer.write("if is_element(item) and element_name(item) == name, do: item");
        writer.dedent();
        writer.write("end)");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp is_element({:xmlElement, _, _, _, _, _, _, _, _, _, _, _}), do: true");
        writer.write("defp is_element({_, _, content, _, _, _}) when is_list(content), do: true");
        writer.write("defp is_element(_), do: false");
        writer.write("");
        writer.write("defp element_name({:xmlElement, name, _, _, _, _, _, _, _, _, _, _}) when is_atom(name),");
        writer.indent();
        writer.write("do: Atom.to_string(name)");
        writer.dedent();
        writer.write("defp element_name({:xmlElement, name, _, _, _, _, _, _, _, _, _, _}) when is_list(name),");
        writer.indent();
        writer.write("do: List.to_string(name)");
        writer.dedent();
        writer.write("defp element_name({:xmlElement, name, _, _, _, _, _, _, _, _, _, _}) when is_binary(name),");
        writer.indent();
        writer.write("do: name");
        writer.dedent();
        writer.write("defp element_name({name, _, _, _, _, _}) when is_atom(name), do: Atom.to_string(name)");
        writer.write("defp element_name({name, _, _, _, _, _}) when is_list(name), do: List.to_string(name)");
        writer.write("defp element_name({name, _, _, _, _, _}) when is_binary(name), do: name");
        writer.write("");
        writer.write("defp xml_child_text(parent, name) do");
        writer.indent();
        writer.write("case find_element(name, element_content(parent)) do");
        writer.indent();
        writer.write("nil -> nil");
        writer.write("element ->");
        writer.indent();
        writer.write("case element_text(element) do");
        writer.indent();
        writer.write("[] -> nil");
        writer.write("[text | _] -> List.to_string(text)");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp element_text({:xmlElement, _, _, _, _, _, _, _, content, _, _, _}) do");
        writer.indent();
        writer.write("Enum.flat_map(content, &collect_text/1)");
        writer.dedent();
        writer.write("end");
        writer.write("defp element_text(_), do: []");
        writer.write("");
        writer.write("defp collect_text({:xmlText, _, _, _, text, _}), do: [text]");
        writer.write("defp collect_text(text) when is_list(text), do: [text]");
        writer.write("defp collect_text(_), do: []");
        writer.write("");
        writer.write("defp is_element_string({:xmlElement, _, _, _, _, _, _, _, _, _, _, _}), do: true");
        writer.write("defp is_element_string(_), do: false");
        writer.write("");
        writer.write("defp xml_child_list(parent, nil, item_name) do");
        writer.indent();
        writer.write("parent");
        writer.write("|> element_content()");
        writer.write("|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
        writer.write("|> Enum.map(fn item ->");
        writer.indent();
        writer.write("case element_text(item) do");
        writer.indent();
        writer.write("[] -> nil");
        writer.write("[text | _] -> List.to_string(text)");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end)");
        writer.write("|> Enum.reject(&is_nil/1)");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp xml_child_list(parent, list_name, item_name) do");
        writer.indent();
        writer.write("case find_element(list_name, element_content(parent)) do");
        writer.indent();
        writer.write("nil -> nil");
        writer.write("list_element ->");
        writer.indent();
        writer.write("list_element");
        writer.write("|> element_content()");
        writer.write("|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
        writer.write("|> Enum.map(fn item ->");
        writer.indent();
        writer.write("case element_text(item) do");
        writer.indent();
        writer.write("[] -> nil");
        writer.write("[text | _] -> List.to_string(text)");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end)");
        writer.write("|> Enum.reject(&is_nil/1)");
        writer.dedent();
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp xml_child_struct_list(parent, nil, item_name, decode_fun) do");
        writer.indent();
        writer.write("parent");
        writer.write("|> element_content()");
        writer.write("|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
        writer.write("|> Enum.map(decode_fun)");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp xml_child_struct_list(parent, list_name, item_name, decode_fun) do");
        writer.indent();
        writer.write("case find_element(list_name, element_content(parent)) do");
        writer.indent();
        writer.write("nil -> nil");
        writer.write("list_element ->");
        writer.indent();
        writer.write("list_element");
        writer.write("|> element_content()");
        writer.write("|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
        writer.write("|> Enum.map(decode_fun)");
        writer.dedent();
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp xml_text(v) when is_binary(v), do: v");
        writer.write("defp xml_text(v) when is_atom(v), do: Atom.to_string(v)");
        writer.write("defp xml_text(v), do: inspect(v)");
        writer.write("");
        if (checksumBindings) {
            ElixirHttpChecksumEmitter.emitChecksumHelpers(writer);
        }
    }

    private static String buildStructureMap(
            Model model, StructureShape structure, String varName, SymbolProvider sp) {
        List<String> entries = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            if (BeamXmlBindingIndex.isXmlAttribute(member)) {
                continue;
            }
            String field = fieldName(sp, member);
            String wireName = BeamXmlBindingIndex.memberElementName(member);
            entries.add("\"" + wireName + "\" => " + varName + "." + field);
        }
        return "%{" + String.join(", ", entries) + "}";
    }

    private static String buildStructureMapFromOutput(
            Model model, StructureShape structure, String varName, SymbolProvider sp) {
        return buildStructureMap(model, structure, varName, sp);
    }

    private static String decodeStructure(
            Model model, StructureShape structure, String xmlVar, SymbolProvider sp) {
        List<String> fields = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            String field = fieldName(sp, member);
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof ListShape listShape) {
                fields.add(field + ": " + decodeListFieldFromXml(model, member, listShape, xmlVar, sp));
            } else {
                fields.add(field + ": xml_child_text(" + xmlVar + ", \""
                        + BeamXmlBindingIndex.memberElementName(member) + "\")");
            }
        }
        return "%Types." + structName(sp, structure) + "{" + String.join(", ", fields) + "}";
    }

    private static String decodeListFieldFromXml(
            Model model,
            MemberShape member,
            ListShape listShape,
            String xmlVar,
            SymbolProvider sp) {
        String element = BeamXmlBindingIndex.memberElementName(member);
        String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
        String listNameArg = element == null ? "nil" : "\"" + element + "\"";
        Shape listMember = model.expectShape(listShape.getMember().getTarget());
        if (listMember instanceof StructureShape nested) {
            String itemStruct = decodeStructure(model, nested, "item", sp);
            return "xml_child_struct_list(" + xmlVar + ", " + listNameArg + ", \""
                    + itemElement + "\", fn item -> " + itemStruct + " end)";
        }
        return "xml_child_list(" + xmlVar + ", " + listNameArg + ", \"" + itemElement + "\")";
    }

    private static String decodeOutputStruct(
            Model model, StructureShape output, String xmlVar, SymbolProvider sp) {
        return decodeStructure(model, output, xmlVar, sp);
    }

    private static String structName(SymbolProvider sp, StructureShape shape) {
        return sp.toSymbol(shape).getName();
    }

    private static String fieldName(SymbolProvider sp, MemberShape member) {
        return BeamNameUtils.toSnakeCase(member.getMemberName());
    }

    private static String buildPathExpression(String uriTemplate, List<HttpBinding> labels, SymbolProvider sp) {
        if (labels.isEmpty()) {
            return "\"" + uriTemplate + "\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        int pos = 0;
        while (pos < uriTemplate.length()) {
            int start = uriTemplate.indexOf('{', pos);
            if (start < 0) {
                sb.append(uriTemplate.substring(pos)).append("\"");
                break;
            }
            if (start > pos) {
                sb.append(uriTemplate, pos, start);
            }
            int end = uriTemplate.indexOf('}', start);
            String labelName = uriTemplate.substring(start + 1, end);
            sb.append("\" <> URI.encode(to_string(input.")
                    .append(fieldName(sp, labels.stream()
                            .filter(l -> l.getLocationName().equals(labelName))
                            .findFirst()
                            .map(HttpBinding::getMember)
                            .orElseThrow()))
                    .append(")) <> \"");
            pos = end + 1;
        }
        if (sb.charAt(sb.length() - 1) == '"') {
            return sb.toString();
        }
        sb.append("\"");
        return sb.toString();
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

    public static boolean serviceEncodesWithConfig(Model model, ServiceShape service) {
        return serviceHasHostLabelOperations(model, service)
                || BeamS3CustomizationIndex.of(model).serviceUsesBucketAddressing(service);
    }
}
