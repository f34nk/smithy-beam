package io.smithy.beam.erlang;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.SparseTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared JSON document encode/decode helpers for REST JSON and AWS JSON RPC emitters.
 */
final class ErlangJsonCodecSupport {

    private ErlangJsonCodecSupport() {}

    static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }

    static String toBindingVar(String snakeField) {
        return BeamNameUtils.toCamelCaseVariable(snakeField);
    }

    static String jsonKey(MemberShape member) {
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }

    static String documentDecodeAssignment(String fieldName, String jsonKey, Shape target) {
        String raw = "maps:get(<<\"" + jsonKey + "\">>, Decoded, undefined)";
        if (target instanceof ListShape) {
            String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
            return fieldName + " = " + helper + "(" + raw + ")";
        }
        if (target instanceof MapShape) {
            if (target.hasTrait(SparseTrait.class)) {
                return fieldName + " = decode_sparse_map(" + raw + ")";
            }
            return fieldName + " = " + raw;
        }
        return fieldName + " = " + raw;
    }

    static String encodeDocumentValue(Shape target, String fieldName) {
        String binding = toBindingVar(fieldName);
        if (target instanceof ListShape && target.hasTrait(SparseTrait.class)) {
            return "encode_sparse_list(" + binding + ")";
        }
        if (target instanceof MapShape && target.hasTrait(SparseTrait.class)) {
            return "encode_sparse_map(" + binding + ")";
        }
        return binding;
    }

    static String timestampEncodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        var fmt = httpIndex.determineTimestampFormat(
                member, location, software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
        return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "encode_timestamp_epoch_seconds"
                : "encode_timestamp_date_time";
    }

    static String timestampDecodeHelper(
            HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
        var fmt = httpIndex.determineTimestampFormat(
                member, location, software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
        return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "decode_timestamp_epoch_seconds"
                : "decode_timestamp_date_time";
    }

    static List<String> inputPatternParts(StructureShape input) {
        List<String> parts = new ArrayList<>();
        for (MemberShape member : input.members()) {
            String field = BeamNameUtils.toSnakeCase(member.getMemberName());
            parts.add(field + " = " + toBindingVar(field));
        }
        return parts;
    }

    static String inputPattern(StructureShape input) {
        List<String> parts = inputPatternParts(input);
        return parts.isEmpty() ? "" : "\n    " + String.join(",\n    ", parts) + "\n";
    }

    static List<MemberShape> documentMembers(
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

    static void emitBodyMapEntries(
            ErlangWriter writer,
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            List<MemberShape> members,
            HttpBinding.Location location,
            String eventStreamModule) {
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String wireKey = jsonKey(member);
            String comma = i < members.size() - 1 ? "," : "";
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof EnumShape || target instanceof IntEnumShape) {
                String helperName = sp.toSymbol(target).getName().replace("()", "");
                writer.write("    <<\"$L\">> => encode_$L($L)$L",
                        wireKey, helperName, toBindingVar(fieldName), comma);
            } else if (target instanceof UnionShape
                    && !BeamEventStreamIndex.of(model).isEventStreamUnion(target)) {
                String helperName = sp.toSymbol(target).getName().replace("()", "");
                writer.write("    <<\"$L\">> => encode_$L($L)$L",
                        wireKey, helperName, toBindingVar(fieldName), comma);
            } else if (target instanceof UnionShape
                    && BeamEventStreamIndex.of(model).isEventStreamUnion(target)) {
                UnionShape union = (UnionShape) target;
                String helper = ErlangEventStreamEmitter.helperName(sp, union);
                writer.write("    <<\"$L\">> => $L:encode_$L($L)$L",
                        wireKey, eventStreamModule, helper, toBindingVar(fieldName), comma);
            } else if (target instanceof TimestampShape) {
                String encodeHelper = timestampEncodeHelper(httpIndex, member, location);
                writer.write("    <<\"$L\">> => $L($L)$L",
                        wireKey, encodeHelper, toBindingVar(fieldName), comma);
            } else {
                writer.write("    <<\"$L\">> => $L$L",
                        wireKey, encodeDocumentValue(target, fieldName), comma);
            }
        }
    }

    static void emitRecordFieldsFromDecoded(
            ErlangWriter writer,
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            List<MemberShape> members,
            HttpBinding.Location location,
            String eventStreamModule) {
        List<String> recordFields = new ArrayList<>();
        for (MemberShape member : members) {
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String wireKey = jsonKey(member);
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof EnumShape || target instanceof IntEnumShape) {
                String helperName = sp.toSymbol(target).getName().replace("()", "");
                recordFields.add("    " + fieldName + " = decode_" + helperName
                        + "(maps:get(<<\"" + wireKey + "\">>, Decoded, undefined))");
            } else if (target instanceof UnionShape
                    && BeamEventStreamIndex.of(model).isEventStreamUnion(target)) {
                UnionShape union = (UnionShape) target;
                String helper = ErlangEventStreamEmitter.helperName(sp, union);
                recordFields.add("    " + fieldName + " = " + eventStreamModule + ":decode_" + helper + "(Body)");
            } else if (target instanceof UnionShape) {
                String helperName = sp.toSymbol(target).getName().replace("()", "");
                recordFields.add("    " + fieldName + " = decode_" + helperName
                        + "(maps:get(<<\"" + wireKey + "\">>, Decoded, undefined))");
            } else if (target instanceof TimestampShape) {
                String decodeHelper = timestampDecodeHelper(httpIndex, member, location);
                recordFields.add("    " + fieldName + " = " + decodeHelper
                        + "(maps:get(<<\"" + wireKey + "\">>, Decoded, undefined))");
            } else {
                recordFields.add("    " + documentDecodeAssignment(fieldName, wireKey, target));
            }
        }
        if (!recordFields.isEmpty()) {
            writer.write(String.join(",\n", recordFields));
        }
    }

    static void emitDecodeJsonBody(ErlangWriter writer) {
        writer.write("Decoded = case Body of");
        writer.indent();
        writer.write("<<>> -> #{};");
        writer.write("_ ->");
        writer.indent();
        writer.write("case jsone:try_decode(Body) of");
        writer.indent();
        writer.write("{ok, Val, _} when is_map(Val) -> Val;");
        writer.write("{error, _} -> #{}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.dedent();
        writer.write("end,");
    }

    static boolean isEventStreamPayload(List<MemberShape> members, Model model) {
        return members.size() == 1 && BeamEventStreamIndex.of(model).isEventStreamMember(members.get(0));
    }
}
