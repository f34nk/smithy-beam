package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamNameUtils;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared AWS JSON RPC codec emitter for Elixir.
 */
final class ElixirAwsJsonRpcEmitter {

    private ElixirAwsJsonRpcEmitter() {}

    static void emitServerCodecModule(
            ElixirContext ctx,
            ServiceShape service,
            ShapeId protocol,
            String contentType,
            String versionLabel) {
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String serverCodecFile = layout.serverCodecModuleName(protocol) + ".ex";
        String serverCodecModule = ElixirSymbolProvider.toModuleName(
                layout.serverCodecModuleName(protocol));
        ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

        ctx.writerDelegator().useFileWriter(serverCodecFile, writer -> {
            writer.write("defmodule $L do", serverCodecModule);
            writer.indent();
            writer.write("@moduledoc \"Server AWS JSON $L codecs for $L (generated).\"",
                    versionLabel, service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            writer.write("");

            for (OperationShape op : operations) {
                emitServerRequestDecoder(writer, model, op, httpIndex, sp, typesMod, runtimeMod);
                emitServerResponseEncoder(writer, model, op, httpIndex, sp, typesMod, runtimeMod, contentType);
            }

            ElixirRestJson1Emitter.emitSharedCodecHelpers(writer, model, service, sp);
            writer.dedent();
            writer.write("end");
        });
    }

    static void emitCodecModule(
            ElixirContext ctx, ServiceShape service, ShapeId protocol, String contentType, String versionLabel) {
        BeamAwsServiceMetadata.from(service).orElseThrow();
        Model model = ctx.model();
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        SymbolProvider sp = ctx.symbolProvider();
        String moduleName = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
        String codecFile = layout.clientCodecModuleName(protocol) + ".ex";
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        String targetPrefix = service.getId().getName();

        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

        ctx.writerDelegator().useFileWriter(codecFile, writer -> {
            writer.write("defmodule $L do", moduleName);
            writer.indent();
            writer.write("@moduledoc \"AWS JSON $L codecs for $L (generated). Do not edit.\"",
                    versionLabel, service.getId());
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: Types", typesMod);
            writer.write("");

            for (OperationShape op : operations) {
                emitEncoder(writer, model, op, httpIndex, sp, typesMod, runtimeMod, targetPrefix, contentType);
                emitDecoder(writer, model, op, httpIndex, sp, typesMod);
            }

            for (OperationShape op : operations) {
                emitErrorDispatch(writer, model, op, sp, typesMod);
            }

            ElixirRestJson1Emitter.emitSharedCodecHelpers(writer, model, service, sp);

            writer.dedent();
            writer.write("end");
        });
    }

    private static void emitServerRequestDecoder(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod,
            String runtimeMod) {

        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputStruct = sp.toSymbol(input).getName();
        String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
        List<MemberShape> members = documentMembers(httpIndex, op, input, true);

        writer.write("@spec decode_$L_request(map()) :: $L", opName, inputType);
        writer.write("def decode_$L_request(%RuntimeTypes.HttpRequest{body: body}) do", opName);
        writer.indent();
        writer.write("decoded = decode_json_body(body)");
        writer.write("%Types.$L{", inputStruct);
        emitStructFieldsFromDecoded(writer, model, httpIndex, sp, members);
        writer.write("}");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitServerResponseEncoder(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod,
            String runtimeMod,
            String contentType) {

        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputStruct = sp.toSymbol(output).getName();
        String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
        List<MemberShape> members = documentMembers(httpIndex, op, output, false);

        writer.write("@spec encode_$L_response($L) :: map()", opName, outputType);
        writer.write("def encode_$L_response(%Types.$L{} = output) do", opName, outputStruct);
        writer.indent();
        writer.write("body_map = %{");
        emitBodyMapEntries(writer, model, httpIndex, sp, members);
        writer.write("}");
        writer.write("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
        writer.write("|> Map.new()");
        writer.write("body = Jason.encode!(body_map)");
        writer.write("headers = [{\"Content-Type\", \"$L\"}]", contentType);
        writer.write("%{status: 200, headers: headers, body: body}");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitEncoder(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            String typesMod,
            String runtimeMod,
            String targetPrefix,
            String contentType) {

        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputStruct = sp.toSymbol(input).getName();
        String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
        String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";
        List<MemberShape> members = documentMembers(httpIndex, op, input, true);
        String amzTarget = targetPrefix + "." + op.getId().getName();

        writer.write("@spec encode_$L_request($L) :: $L", opName, inputType, httpRequestType);
        writer.write("def encode_$L_request(%Types.$L{} = input) do", opName, inputStruct);
        writer.indent();
        writer.write("body_map = %{");
        emitBodyMapEntries(writer, model, httpIndex, sp, members);
        writer.write("}");
        writer.write("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
        writer.write("|> Map.new()");
        writer.write("body = Jason.encode!(body_map)");
        writer.write("%RuntimeTypes.HttpRequest{");
        writer.write("  method: \"POST\",");
        writer.write("  path: \"/\",");
        writer.write("  query: %{},");
        writer.write("  headers: [");
        writer.write("    {\"Content-Type\", \"$L\"},", contentType);
        writer.write("    {\"X-Amz-Target\", \"$L\"}", amzTarget);
        writer.write("  ],");
        writer.write("  body: body");
        writer.write("}");
        writer.dedent();
        writer.write("end");
        writer.write("");
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
        String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
        List<MemberShape> members = documentMembers(httpIndex, op, output, false);

        writer.write("@spec decode_$L_response(map()) :: {:ok, $L} | {:error, term()}", opName, outputType);
        writer.write(
                "def decode_$L_response(%RuntimeTypes.HttpResponse{status: 200, body: body}) do", opName);
        writer.indent();
        writer.write("decoded = if body == \"\" or is_nil(body), do: %{}, else: Jason.decode!(body)");
        writer.write("{:ok, %Types.$L{", outputStruct);
        emitStructFieldsFromDecoded(writer, model, httpIndex, sp, members);
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
            List<String> fields = buildErrorFields(errShape);
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
                List<String> fields = buildErrorFields(errShape);
                writer.write("\"$L\" -> {:error, struct!($L.$L, %{$L})}", localName, typesMod, modName,
                        String.join(", ", fields));
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

    private static List<String> buildErrorFields(StructureShape errShape) {
        List<String> fields = new ArrayList<>();
        for (MemberShape member : errShape.members()) {
            if (member.getMemberName().equals("__beam_error_kind")) {
                continue;
            }
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            fields.add(field + ": Map.get(decoded, \"" + member.getMemberName() + "\")");
        }
        return fields;
    }

    private static List<MemberShape> documentMembers(
            HttpBindingIndex httpIndex,
            OperationShape op,
            StructureShape structure,
            boolean request) {
        List<HttpBinding> bindings = request
                ? httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT)
                : httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
        if (bindings.isEmpty() && request) {
            bindings = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
        } else if (bindings.isEmpty()) {
            bindings = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
        }
        if (!bindings.isEmpty()) {
            return bindings.stream().map(HttpBinding::getMember).collect(Collectors.toList());
        }
        return new ArrayList<>(structure.members());
    }

    private static void emitBodyMapEntries(
            ElixirWriter writer,
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            List<MemberShape> members) {
        for (MemberShape member : members) {
            String field = fieldName(sp, member);
            String wireKey = jsonKey(member);
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof EnumShape || target instanceof IntEnumShape) {
                String helperName = enumHelperName(target);
                writer.write("  \"$L\" => encode_$L(input.$L),", wireKey, helperName, field);
            } else if (target instanceof UnionShape) {
                String helperName = unionHelperName(target);
                writer.write("  \"$L\" => encode_$L(input.$L),", wireKey, helperName, field);
            } else if (target instanceof TimestampShape) {
                String encodeHelper = timestampEncodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
                writer.write("  \"$L\" => $L(input.$L),", wireKey, encodeHelper, field);
            } else {
                writer.write("  \"$L\" => input.$L,", wireKey, field);
            }
        }
    }

    private static void emitStructFieldsFromDecoded(
            ElixirWriter writer,
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            List<MemberShape> members) {
        for (MemberShape member : members) {
            String field = fieldName(sp, member);
            String wireKey = jsonKey(member);
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof EnumShape || target instanceof IntEnumShape) {
                String helperName = enumHelperName(target);
                writer.write("  $L: decode_$L(Map.get(decoded, \"$L\")),", field, helperName, wireKey);
            } else if (target instanceof UnionShape) {
                String helperName = unionHelperName(target);
                writer.write("  $L: decode_$L(Map.get(decoded, \"$L\")),", field, helperName, wireKey);
            } else if (target instanceof TimestampShape) {
                String decodeHelper = timestampDecodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
                writer.write("  $L: $L(Map.get(decoded, \"$L\")),", field, decodeHelper, wireKey);
            } else {
                writer.write("  $L: Map.get(decoded, \"$L\"),", field, wireKey);
            }
        }
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

    private static String enumHelperName(Shape target) {
        return BeamNameUtils.toSnakeCase(target.getId().getName());
    }

    private static String unionHelperName(Shape target) {
        return BeamNameUtils.toSnakeCase(target.getId().getName());
    }

    private static String timestampEncodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        var fmt = httpIndex.determineTimestampFormat(
                member, location, software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
        return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "encode_timestamp_epoch_seconds"
                : "encode_timestamp_date_time";
    }

    private static String timestampDecodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        var fmt = httpIndex.determineTimestampFormat(
                member, location, software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
        return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "decode_timestamp_epoch_seconds"
                : "decode_timestamp_date_time";
    }
}
