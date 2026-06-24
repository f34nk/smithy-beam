package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import software.amazon.smithy.codegen.core.Symbol;
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
public final class ErlangEventStreamEmitter {

    private ErlangEventStreamEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (!BeamEdition.fromSettings(ctx.settings()).supportsEventStreams()) {
            return;
        }
        BeamEventStreamIndex index = BeamEventStreamIndex.of(ctx.model());
        List<UnionShape> unions = index.eventStreamUnions(service);
        if (unions.isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String moduleName = layout.eventStreamModuleName();
        SymbolProvider sp = ctx.symbolProvider();
        Model model = ctx.model();

        List<String> exports = new ArrayList<>();
        for (UnionShape union : unions) {
            String helper = helperName(sp, union);
            exports.add("encode_" + helper + "/1");
            exports.add("decode_" + helper + "/1");
        }

        ctx.writerDelegator().useFileWriter(layout.eventStreamModuleFile(), writer -> {
            writer.write("%% Generated Amazon Event Stream helpers for $L.", service.getId());
            writer.write("-module($L).", moduleName);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            ErlangFormat.writeExport(writer, exports);
            writer.write("");

            for (UnionShape union : unions) {
                ErlangEventStreamIr.writeFunctions(writer, ErlangEventStreamIr.unionHelpers(model, union, sp));
            }

            ErlangEventStreamIr.writeFunctions(writer, List.of(
                    ErlangEventStreamIr.encodeEventHeaders(),
                    ErlangEventStreamIr.headerValue()));
        });
    }

    static void emitUnionEncodeList(ErlangWriter writer, UnionShape union, SymbolProvider sp) {
        String helper = helperName(sp, union);
        writer.write("%% Event stream helpers for $L", union.getId());
        writer.write("encode_$L(Events) when is_list(Events) ->", helper);
        writer.indent();
        writer.write("[encode_$L_event(E) || E <- Events].", helper);
        writer.dedent();
    }

    static void emitUnionDecodeList(ErlangWriter writer, UnionShape union, SymbolProvider sp) {
        String helper = helperName(sp, union);
        writer.write("decode_$L(Body) when is_binary(Body) ->", helper);
        writer.indent();
        writer.write("[decode_$L_event(F) || F <- aws_event_stream:decode_frames(Body)].", helper);
        writer.dedent();
    }

    static void emitUnionEncodeEventClauses(
            ErlangWriter writer, Model model, UnionShape union, SymbolProvider sp) {
        String helper = helperName(sp, union);
        List<MemberShape> members = new ArrayList<>(union.members());
        for (int i = 0; i < members.size(); i++) {
            emitEncodeEventClause(writer, model, helper, members.get(i), sp, ";");
        }
        writer.write("encode_$L_event({unknown, _}) ->", helper);
        writer.indent();
        writer.write("error({bad_event, unknown}).");
        writer.dedent();
    }

    static void emitUnionDecodeEvent(ErlangWriter writer, UnionShape union, SymbolProvider sp) {
        String helper = helperName(sp, union);
        writer.write("decode_$L_event(#{headers := Headers, payload := Payload}) ->", helper);
        writer.indent();
        writer.write("EventType = header_value(Headers, <<\":event-type\">>),");
        writer.write("decode_$L_event_type(EventType, Payload).", helper);
        writer.dedent();
    }

    static void emitUnionDecodeEventTypeClauses(
            ErlangWriter writer, Model model, UnionShape union, SymbolProvider sp) {
        String helper = helperName(sp, union);
        List<MemberShape> members = new ArrayList<>(union.members());
        for (int i = 0; i < members.size(); i++) {
            emitDecodeEventTypeClause(writer, model, helper, members.get(i), sp, ";");
        }
        writer.write("decode_$L_event_type(EventType, _Payload) ->", helper);
        writer.indent();
        writer.write("error({bad_event, EventType}).");
        writer.dedent();
    }

    private static void emitEncodeEventClause(
            ErlangWriter writer,
            Model model,
            String helper,
            MemberShape member,
            SymbolProvider sp,
            String clauseEnd) {
        String tag = unionTagForMember(sp, member);
        String eventType = member.getMemberName();
        Shape target = model.expectShape(member.getTarget());
        writer.write("encode_$L_event({$L, Value}) ->", helper, tag);
        writer.indent();
        writer.write("Payload = $L,", encodeMemberPayload(model, target, "Value", sp));
        writer.write("Headers = encode_event_headers(<<\"$L\">>),", eventType);
        writer.write("aws_event_stream:frame(Headers, Payload)$L", clauseEnd);
        writer.dedent();
    }

    private static void emitDecodeEventTypeClause(
            ErlangWriter writer,
            Model model,
            String helper,
            MemberShape member,
            SymbolProvider sp,
            String clauseEnd) {
        String tag = unionTagForMember(sp, member);
        String eventType = member.getMemberName();
        Shape target = model.expectShape(member.getTarget());
        writer.write("decode_$L_event_type(<<\"$L\">>, Payload) ->", helper, eventType);
        writer.indent();
        writer.write("{$L, $L}$L", tag, decodeMemberPayload(model, target, "Payload", sp), clauseEnd);
        writer.dedent();
    }

    private static String encodeMemberPayload(Model model, Shape target, String valueVar, SymbolProvider sp) {
        if (target instanceof StructureShape structure) {
            return encodeStructurePayload(structure, valueVar, sp);
        }
        if (target instanceof BlobShape || target instanceof StringShape) {
            return valueVar;
        }
        return "jsone:encode(" + valueVar + ")";
    }

    private static String decodeMemberPayload(Model model, Shape target, String payloadVar, SymbolProvider sp) {
        if (target instanceof StructureShape structure) {
            return decodeStructurePayload(structure, payloadVar, sp);
        }
        if (target instanceof BlobShape || target instanceof StringShape) {
            return payloadVar;
        }
        return "jsone:decode(" + payloadVar + ", [return_maps])";
    }

    private static String encodeStructurePayload(
            StructureShape structure, String valueVar, SymbolProvider sp) {
        String recordName = recordName(sp.toSymbol(structure));
        if (structure.members().isEmpty()) {
            return "jsone:encode(#{})";
        }
        StringBuilder map = new StringBuilder("jsone:encode(maps:filter(fun(_, V) -> V =/= undefined end, #{");
        List<MemberShape> members = new ArrayList<>(structure.members());
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String wireKey = jsonKey(member);
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String comma = i < members.size() - 1 ? "," : "";
            map.append("\n        <<\"").append(wireKey).append("\">> => ")
                    .append(valueVar)
                    .append("#")
                    .append(recordName)
                    .append(".")
                    .append(fieldName)
                    .append(comma);
        }
        map.append("\n    }))");
        return map.toString();
    }

    private static String decodeStructurePayload(
            StructureShape structure, String payloadVar, SymbolProvider sp) {
        String recordName = recordName(sp.toSymbol(structure));
        if (structure.members().isEmpty()) {
            return "#" + recordName + "{}";
        }
        StringBuilder record = new StringBuilder("begin\n        Decoded = jsone:decode(")
                .append(payloadVar)
                .append(", [return_maps]),\n        #")
                .append(recordName)
                .append("{\n");
        List<MemberShape> members = new ArrayList<>(structure.members());
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String wireKey = jsonKey(member);
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String comma = i < members.size() - 1 ? "," : "";
            record.append("            ")
                    .append(fieldName)
                    .append(" = maps:get(<<\"")
                    .append(wireKey)
                    .append("\">>, Decoded, undefined)")
                    .append(comma)
                    .append("\n");
        }
        record.append("        }\n    end");
        return record.toString();
    }

    static void emitEncodeEventHeaders(ErlangWriter writer) {
        writer.write("encode_event_headers(EventType) ->");
        writer.indent();
        writer.write("[");
        writer.write("    {<<\":event-type\">>, EventType},");
        writer.write("    {<<\":message-type\">>, <<\"event\">>},");
        writer.write("    {<<\":content-type\">>, <<\"application/json\">>}");
        writer.write("].");
        writer.dedent();
    }

    static void emitHeaderValue(ErlangWriter writer) {
        writer.write("header_value(Headers, Name) ->");
        writer.indent();
        writer.write("proplists:get_value(Name, Headers, undefined).");
        writer.dedent();
    }

    static String helperName(SymbolProvider sp, UnionShape union) {
        return sp.toSymbol(union).getName().replace("()", "");
    }

    static String unionTagForMember(SymbolProvider sp, MemberShape member) {
        return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
    }

    private static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }

    private static String jsonKey(MemberShape member) {
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }
}
