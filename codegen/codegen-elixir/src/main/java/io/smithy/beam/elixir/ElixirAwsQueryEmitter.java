package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsQueryFormEncoder;
import io.smithy.beam.core.BeamAwsQueryProtocolCodegen;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamEc2QueryProtocolCodegen;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamXmlDecoder;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AWS Query protocol codec emitter for Elixir clients.
 */
public final class ElixirAwsQueryEmitter {

    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private ElixirAwsQueryEmitter() {}

    static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
        emitCodecModule(ctx, service, BeamAwsQueryProtocolCodegen.AWS_QUERY);
    }

    static void emitCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
        boolean ec2Query = BeamEc2QueryProtocolCodegen.EC2_QUERY.equals(protocolTraitId);
        BeamAwsServiceMetadata.from(service).orElseThrow();
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        String moduleName = ElixirSymbolProvider.toModuleName(
                layout.clientCodecModuleName(protocolTraitId));
        String codecFile = layout.clientCodecModuleName(protocolTraitId) + ".ex";
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
        Set<StructureShape> inputShapes = operations.stream()
                .map(op -> model.expectShape(op.getInputShape(), StructureShape.class))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ctx.writerDelegator().useFileWriter(codecFile, writer -> {
            writer.write("defmodule $L do", moduleName);
            writer.indent();
            writer.write("@moduledoc \"AWS Query codecs for $L (generated). Do not edit.\"", service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            writer.write("");

            for (OperationShape op : operations) {
                emitEncoder(writer, model, service, op, sp, typesMod, runtimeMod);
                emitDecoder(writer, model, service, op, sp, typesMod, ec2Query);
            }

            for (StructureShape input : inputShapes) {
                emitFlattenInputClause(writer, sp, input, ec2Query);
            }

            emitQueryHelpers(writer, ec2Query);
            emitXmlHelpers(writer, ec2Query);

            writer.dedent();
            writer.write("end");
        });
    }

    private static void emitEncoder(
            ElixirWriter writer,
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            String typesMod,
            String runtimeMod) {

        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputStruct = sp.toSymbol(input).getName();
        String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
        String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";
        String action = BeamAwsQueryFormEncoder.operationAction(op, service);
        String version = BeamAwsQueryFormEncoder.serviceVersion(service);

        writer.write("@spec encode_$L_request($L) :: $L", opName, inputType, httpRequestType);
        writer.write("def encode_$L_request(%Types.$L{} = input) do", opName, inputStruct);
        writer.indent();
        writer.write("pairs = [");
        writer.write("  {\"Action\", \"$L\"},", action);
        writer.write("  {\"Version\", \"$L\"}", version);
        writer.write("  | flatten_query_input(input)");
        writer.write("]");
        writer.write("body =");
        writer.indent();
        writer.write("pairs");
        writer.write("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
        writer.write("|> Enum.map(fn {k, v} -> {k, enc(v)} end)");
        writer.write("|> URI.encode_query()");
        writer.dedent();
        writer.write("%RuntimeTypes.HttpRequest{");
        writer.write("  method: \"POST\",");
        writer.write("  path: \"/\",");
        writer.write("  query: %{},");
        writer.write("  headers: [{\"Content-Type\", \"$L\"}],", CONTENT_TYPE);
        writer.write("  body: body");
        writer.write("}");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitDecoder(
            ElixirWriter writer,
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            String typesMod,
            boolean ec2Query) {

        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputStruct = sp.toSymbol(output).getName();
        String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
        String resultElement = ec2Query
                ? BeamXmlDecoder.ec2QueryResultElementName(op, service)
                : BeamXmlDecoder.queryResultElementName(op, service);

        writer.write("@spec decode_$L_response(map()) :: {:ok, $L} | {:error, term()}", opName, outputType);
        writer.write("def decode_$L_response(%RuntimeTypes.HttpResponse{status: 200, body: body}) do", opName);
        writer.indent();
        writer.write("case unwrap_query_result(body, \"$L\") do", resultElement);
        writer.indent();
        writer.write("{:ok, result} ->");
        writer.indent();
        writer.write("{:ok, %Types.$L{", outputStruct);
        emitOutputFields(writer, model, sp, output);
        writer.write("}}");
        writer.dedent();
        writer.write("{:error, reason} ->");
        writer.indent();
        writer.write("{:error, reason}");
        writer.dedent();
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("def decode_$L_response(%RuntimeTypes.HttpResponse{status: status, body: body}) do", opName);
        writer.indent();
        writer.write("decode_query_error(status, body)");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitOutputFields(
            ElixirWriter writer,
            Model model,
            SymbolProvider sp,
            StructureShape output) {

        List<MemberShape> members = new ArrayList<>(output.members());
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String field = fieldName(sp, member);
            String element = BeamXmlDecoder.memberElementName(member);
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof ListShape listShape) {
                String itemElement = BeamXmlDecoder.listItemElementName(listShape);
                if (i < members.size() - 1) {
                    writer.write("  $L: xml_child_list(result, \"$L\", \"$L\"),", field, element, itemElement);
                } else {
                    writer.write("  $L: xml_child_list(result, \"$L\", \"$L\")", field, element, itemElement);
                }
            } else {
                if (i < members.size() - 1) {
                    writer.write("  $L: xml_child_text(result, \"$L\"),", field, element);
                } else {
                    writer.write("  $L: xml_child_text(result, \"$L\")", field, element);
                }
            }
        }
    }

    private static void emitFlattenInputClause(
            ElixirWriter writer,
            SymbolProvider sp,
            StructureShape input,
            boolean ec2Query) {

        String inputStruct = sp.toSymbol(input).getName();
        List<MemberShape> members = new ArrayList<>(input.members());

        writer.write("defp flatten_query_input(%Types.$L{", inputStruct);
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String field = fieldName(sp, member);
            if (i < members.size() - 1) {
                writer.write("  $L: $L,", field, field);
            } else {
                writer.write("  $L: $L", field, field);
            }
        }
        writer.write("}) do");
        writer.indent();
        if (members.isEmpty()) {
            writer.write("[]");
        } else {
            writer.write("[");
            for (int i = 0; i < members.size(); i++) {
                MemberShape member = members.get(i);
                String field = fieldName(sp, member);
                String wireKey = queryFormKey(member, ec2Query);
                if (i < members.size() - 1) {
                    writer.write("  flatten_member(\"$L\", $L),", wireKey, field);
                } else {
                    writer.write("  flatten_member(\"$L\", $L)", wireKey, field);
                }
            }
            writer.write("]");
            writer.write("|> List.flatten()");
        }
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitQueryHelpers(ElixirWriter writer, boolean ec2Query) {
        writer.write("defp flatten_member(_key, nil), do: []");
        if (ec2Query) {
            writer.write("defp flatten_member(key, value) when is_list(value) do");
            writer.indent();
            writer.write("value");
            writer.write("|> Enum.with_index(1)");
            writer.write("|> Enum.flat_map(fn {v, i} ->");
            writer.indent();
            writer.write("if is_nil(v), do: [], else: flatten_member(\"#{key}.#{i}\", v)");
            writer.dedent();
            writer.write("end)");
            writer.dedent();
            writer.write("");
        } else {
            writer.write("defp flatten_member(key, value) when is_list(value) do");
            writer.indent();
            writer.write("value");
            writer.write("|> Enum.with_index(1)");
            writer.write("|> Enum.flat_map(fn {v, i} ->");
            writer.indent();
            writer.write("if is_nil(v), do: [], else: flatten_member(\"#{key}.member.#{i}\", v)");
            writer.dedent();
            writer.write("end)");
            writer.dedent();
            writer.write("");
        }
        writer.write("defp flatten_member(key, value) when is_map(value) do");
        writer.indent();
        writer.write("value");
        writer.write("|> Map.to_list()");
        writer.write("|> Enum.with_index(1)");
        writer.write("|> Enum.flat_map(fn {{k, v}, i} ->");
        writer.indent();
        writer.write("if is_nil(k) or is_nil(v), do: [],");
        writer.write("else: flatten_member(\"#{key}.entry.#{i}.key\", k) ++ flatten_member(\"#{key}.entry.#{i}.value\", v)");
        writer.dedent();
        writer.write("end)");
        writer.dedent();
        writer.write("");
        writer.write("defp flatten_member(key, value), do: [{key, value}]");
        writer.write("");
        writer.write("defp enc(value) when is_boolean(value), do: Atom.to_string(value)");
        writer.write("defp enc(value) when is_integer(value), do: Integer.to_string(value)");
        writer.write("defp enc(value) when is_float(value), do: Float.to_string(value)");
        writer.write("defp enc(value) when is_binary(value), do: value");
        writer.write("defp enc(value) when is_atom(value), do: Atom.to_string(value)");
        writer.write("");
    }

    private static void emitXmlHelpers(ElixirWriter writer, boolean ec2Query) {
        writer.write("defp unwrap_query_result(body, result_name) do");
        writer.indent();
        writer.write("case :xmerl_scan.string(:erlang.binary_to_list(body)) do");
        writer.indent();
        writer.write("{:xmlElement, _, _, _, _, _, _, _, content, _, _, _} = xml, _} ->");
        writer.indent();
        writer.write("case find_element(result_name, element_content(xml)) do");
        writer.indent();
        writer.write("nil -> {:error, {:missing_result, result_name}}");
        writer.write("result -> {:ok, result}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("_ -> {:error, :xml_parse_error}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("rescue");
        writer.indent();
        writer.write("reason -> {:error, {:xml_parse_error, reason}}");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp element_content({:xmlElement, _, _, _, _, _, _, _, content, _, _, _}), do: content");
        writer.write("defp element_content([h | _]), do: element_content(h)");
        writer.write("defp element_content(_), do: []");
        writer.write("");
        writer.write("defp find_element(name, content) do");
        writer.indent();
        writer.write("content");
        writer.write("|> Enum.find(fn");
        writer.indent();
        writer.write("item -> is_element(item) and element_name(item) == name");
        writer.dedent();
        writer.write("end)");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp is_element({:xmlElement, _, _, _, _, _, _, _, _, _, _, _}), do: true");
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
        writer.write("text -> List.to_string(text)");
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
        writer.write("Enum.filter(content, fn item -> is_list(item) and not is_element_string(item) end)");
        writer.dedent();
        writer.write("end");
        writer.write("defp element_text(_), do: []");
        writer.write("");
        writer.write("defp is_element_string({:xmlElement, _, _, _, _, _, _, _, _, _, _, _}), do: true");
        writer.write("defp is_element_string(_), do: false");
        writer.write("");
        writer.write("defp xml_child_list(parent, list_name, item_name) do");
        writer.indent();
        writer.write("case find_element(list_name, element_content(parent)) do");
        writer.indent();
        writer.write("nil -> nil");
        writer.write("list_element ->");
        writer.indent();
        writer.write("element_content(list_element)");
        writer.write("|> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)");
        writer.write("|> Enum.map(fn item ->");
        writer.indent();
        writer.write("case element_text(item) do");
        writer.indent();
        writer.write("[] -> nil");
        writer.write("text -> List.to_string(text)");
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
        if (ec2Query) {
            emitEc2QueryErrorDecoder(writer);
        } else {
            emitAwsQueryErrorDecoder(writer);
        }
        writer.write("");
    }

    private static void emitAwsQueryErrorDecoder(ElixirWriter writer) {
        writer.write("defp decode_query_error(status, body) do");
        writer.indent();
        writer.write("try do");
        writer.indent();
        writer.write("{:xmlElement, _, _, _, _, _, _, _, _, _, _, _} = xml =");
        writer.indent();
        writer.write("case :xmerl_scan.string(:erlang.binary_to_list(body)) do");
        writer.indent();
        writer.write("{doc, _} -> doc");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("case find_element(\"$L\", element_content(xml)) do", BeamXmlDecoder.ERROR_RESPONSE_ELEMENT);
        writer.indent();
        writer.write("nil -> {:error, {:unknown_error, status, body}}");
        writer.write("error_response ->");
        writer.indent();
        writer.write("case find_element(\"$L\", element_content(error_response)) do", BeamXmlDecoder.ERROR_ELEMENT);
        writer.indent();
        writer.write("nil -> {:error, {:unknown_error, status, body}}");
        writer.write("error ->");
        writer.indent();
        writer.write("{:error, {");
        writer.write("  xml_child_text(error, \"$L\"),", BeamXmlDecoder.ERROR_CODE_ELEMENT);
        writer.write("  xml_child_text(error, \"$L\")", BeamXmlDecoder.ERROR_MESSAGE_ELEMENT);
        writer.write("}}");
        writer.dedent();
        writer.dedent();
        writer.dedent();
        writer.dedent();
        writer.write("rescue");
        writer.indent();
        writer.write("_ -> {:error, {:unknown_error, status, body}}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
    }

    private static void emitEc2QueryErrorDecoder(ElixirWriter writer) {
        writer.write("defp decode_query_error(status, body) do");
        writer.indent();
        writer.write("try do");
        writer.indent();
        writer.write("{:xmlElement, _, _, _, _, _, _, _, _, _, _, _} = xml =");
        writer.indent();
        writer.write("case :xmerl_scan.string(:erlang.binary_to_list(body)) do");
        writer.indent();
        writer.write("{doc, _} -> doc");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("case find_element(\"$L\", element_content(xml)) do", BeamXmlDecoder.EC2_RESPONSE_ELEMENT);
        writer.indent();
        writer.write("nil -> {:error, {:unknown_error, status, body}}");
        writer.write("response ->");
        writer.indent();
        writer.write("case find_element(\"$L\", element_content(response)) do", BeamXmlDecoder.EC2_ERRORS_ELEMENT);
        writer.indent();
        writer.write("nil -> {:error, {:unknown_error, status, body}}");
        writer.write("errors ->");
        writer.indent();
        writer.write("case find_element(\"$L\", element_content(errors)) do", BeamXmlDecoder.ERROR_ELEMENT);
        writer.indent();
        writer.write("nil -> {:error, {:unknown_error, status, body}}");
        writer.write("error ->");
        writer.indent();
        writer.write("{:error, {");
        writer.write("  xml_child_text(error, \"$L\"),", BeamXmlDecoder.ERROR_CODE_ELEMENT);
        writer.write("  xml_child_text(error, \"$L\")", BeamXmlDecoder.ERROR_MESSAGE_ELEMENT);
        writer.write("}}");
        writer.dedent();
        writer.dedent();
        writer.dedent();
        writer.dedent();
        writer.dedent();
        writer.write("rescue");
        writer.indent();
        writer.write("_ -> {:error, {:unknown_error, status, body}}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
    }

    private static String fieldName(SymbolProvider sp, MemberShape member) {
        return BeamNameUtils.toSnakeCase(member.getMemberName());
    }

    private static String queryFormKey(MemberShape member, boolean ec2Query) {
        return ec2Query
                ? BeamAwsQueryFormEncoder.ec2QueryFormKey(member)
                : BeamAwsQueryFormEncoder.awsQueryFormKey(member);
    }
}
