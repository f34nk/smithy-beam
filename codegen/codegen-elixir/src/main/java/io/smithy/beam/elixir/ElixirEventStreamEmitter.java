package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.JsonNameTrait;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits Amazon Event Stream encode and decode helpers for {@code @streaming} union shapes.
 */
public final class ElixirEventStreamEmitter {

    private ElixirEventStreamEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        if (!BeamEdition.fromSettings(ctx.settings()).supportsEventStreams()) {
            return;
        }
        BeamEventStreamIndex index = BeamEventStreamIndex.of(ctx.model());
        List<UnionShape> unions = index.eventStreamUnions(service);
        if (unions.isEmpty()) {
            return;
        }

        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String moduleName = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
        String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        SymbolProvider sp = ctx.symbolProvider();
        Model model = ctx.model();

        ctx.writerDelegator().useFileWriter(layout.eventStreamModuleFile(), writer -> {
            writer.write("defmodule $L do", moduleName);
            writer.indent();
            writer.write("@moduledoc \"Generated Amazon Event Stream helpers for $L (generated).\"", service.getId());
            writer.write("");
            writer.write("alias $L", typesMod);
            writer.write("");

            for (UnionShape union : unions) {
                emitUnionEventStream(writer, model, union, sp, typesMod);
            }

            writer.write("defp header_value(headers, name) do");
            writer.indent();
            writer.write("Enum.find_value(headers, fn {key, value} -> if key == name, do: value end)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp encode_event_headers(event_type) do");
            writer.indent();
            writer.write("[");
            writer.write("  {\":event-type\", event_type},");
            writer.write("  {\":message-type\", \"event\"},");
            writer.write("  {\":content-type\", \"application/json\"}");
            writer.write("]");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }

    private static void emitUnionEventStream(
            ElixirWriter writer,
            Model model,
            UnionShape union,
            SymbolProvider sp,
            String typesMod) {
        String helper = helperName(sp, union);

        writer.write("@doc \"Encodes a list of event stream events into framed binaries.\"");
        writer.write("def encode_$L(events) when is_list(events) do", helper);
        writer.indent();
        writer.write("Enum.map(events, &encode_$L_event/1)", helper);
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("@doc \"Decodes an event stream body into tagged events.\"");
        writer.write("def decode_$L(body) when is_binary(body) do", helper);
        writer.indent();
        writer.write("body");
        ElixirFormat.writePipelineStep(writer, "AwsEventStream.decode_frames()");
        ElixirFormat.writePipelineStep(writer, "Enum.map(&decode_" + helper + "_event/1)");
        writer.dedent();
        writer.write("end");
        writer.write("");

        for (MemberShape member : union.members()) {
            emitEncodeEventClause(writer, model, helper, member, sp, typesMod);
        }
        writer.write("defp encode_$L_event({:unknown, _}), do: raise ArgumentError, \"unknown event\"", helper);
        writer.write("");

        writer.write("defp decode_$L_event(%{headers: headers, payload: payload}) do", helper);
        writer.indent();
        writer.write("event_type = header_value(headers, \":event-type\")");
        writer.write("decode_$L_event_type(event_type, payload)", helper);
        writer.dedent();
        writer.write("end");
        writer.write("");

        for (MemberShape member : union.members()) {
            emitDecodeEventTypeClause(writer, model, helper, member, sp, typesMod);
        }
        writer.write("defp decode_$L_event_type(event_type, _payload) do", helper);
        writer.indent();
        writer.write("raise ArgumentError, \"unknown event type: \" <> inspect(event_type)");
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static void emitEncodeEventClause(
            ElixirWriter writer,
            Model model,
            String helper,
            MemberShape member,
            SymbolProvider sp,
            String typesMod) {
        String tag = unionTagForMember(sp, member);
        String eventType = member.getMemberName();
        Shape target = model.expectShape(member.getTarget());
        writer.write("defp encode_$L_event({$L, value}) do", helper, tag);
        writer.indent();
        writer.write("payload = $L", encodeMemberPayload(model, target, "value", sp, typesMod));
        writer.write("headers = encode_event_headers(\"$L\")", eventType);
        writer.write("AwsEventStream.frame(headers, payload)");
        writer.dedent();
        writer.write("end");
    }

    private static void emitDecodeEventTypeClause(
            ElixirWriter writer,
            Model model,
            String helper,
            MemberShape member,
            SymbolProvider sp,
            String typesMod) {
        String tag = unionTagForMember(sp, member);
        String eventType = member.getMemberName();
        Shape target = model.expectShape(member.getTarget());
        writer.write("defp decode_$L_event_type(\"$L\", payload) do", helper, eventType);
        writer.indent();
        writer.write("{$L, $L}", tag, decodeMemberPayload(model, target, "payload", sp, typesMod));
        writer.dedent();
        writer.write("end");
    }

    private static String encodeMemberPayload(
            Model model, Shape target, String valueVar, SymbolProvider sp, String typesMod) {
        if (target instanceof StructureShape structure) {
            return encodeStructurePayload(structure, valueVar, sp);
        }
        if (target instanceof BlobShape || target instanceof StringShape) {
            return valueVar;
        }
        return "Jason.encode!(" + valueVar + ")";
    }

    private static String decodeMemberPayload(
            Model model, Shape target, String payloadVar, SymbolProvider sp, String typesMod) {
        if (target instanceof StructureShape structure) {
            return decodeStructurePayload(structure, payloadVar, sp, typesMod);
        }
        if (target instanceof BlobShape || target instanceof StringShape) {
            return payloadVar;
        }
        return "Jason.decode!(" + payloadVar + ")";
    }

    private static String encodeStructurePayload(
            StructureShape structure, String valueVar, SymbolProvider sp) {
        if (structure.members().isEmpty()) {
            return "Jason.encode!(%{})";
        }
        StringBuilder map = new StringBuilder("Jason.encode!(%{\n");
        List<MemberShape> members = new ArrayList<>(structure.members());
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String wireKey = jsonKey(member);
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String comma = i < members.size() - 1 ? "," : "";
            map.append("      \"")
                    .append(wireKey)
                    .append("\" => Map.get(")
                    .append(valueVar)
                    .append(", :")
                    .append(fieldName)
                    .append(")")
                    .append(comma)
                    .append("\n");
        }
        map.append("    })");
        return map.toString();
    }

    private static String decodeStructurePayload(
            StructureShape structure, String payloadVar, SymbolProvider sp, String typesMod) {
        String structName = sp.toSymbol(structure).getName();
        if (structure.members().isEmpty()) {
            return "%" + typesMod + "." + structName + "{}";
        }
        StringBuilder struct = new StringBuilder("case Jason.decode!(")
                .append(payloadVar)
                .append(") do\n      decoded ->\n        %")
                .append(typesMod)
                .append(".")
                .append(structName)
                .append("{\n");
        List<MemberShape> members = new ArrayList<>(structure.members());
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String wireKey = jsonKey(member);
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String comma = i < members.size() - 1 ? "," : "";
            struct.append("          ")
                    .append(fieldName)
                    .append(": Map.get(decoded, \"")
                    .append(wireKey)
                    .append("\")")
                    .append(comma)
                    .append("\n");
        }
        struct.append("        }\n    end");
        return struct.toString();
    }

    static String helperName(SymbolProvider sp, UnionShape union) {
        return sp.toSymbol(union).getName().replace("()", "");
    }

    static String unionTagForMember(SymbolProvider sp, MemberShape member) {
        return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
    }

    private static String jsonKey(MemberShape member) {
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }
}
