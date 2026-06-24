package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRecordAccess;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.SparseTrait;

import java.util.ArrayList;
import java.util.List;

final class ErlangStructureHelperIr {
    private ErlangStructureHelperIr() {}

    static List<ErlFunction> structureDecodeEncode(
            Model model,
            HttpBindingIndex httpIndex,
            StructureShape structure,
            SymbolProvider sp) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, structure);
        return List.of(
                structureDecode(model, httpIndex, structure, sp, helperName),
                structureEncode(model, httpIndex, structure, sp, helperName));
    }

    private static ErlFunction structureDecode(
            Model model,
            HttpBindingIndex httpIndex,
            StructureShape structure,
            SymbolProvider sp,
            String helperName) {
        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
            String wireKey = ErlangJsonCodecSupport.jsonKey(member);
            ErlExpr raw = ErlangCodecHelperIr.mapsGetDefault(
                    ErlBinary.binary(wireKey), ErlVar.var("Map"), ErlAtom.atom("undefined"));
            fields.add(ErlRecordField.field(fieldName, decodeFieldValue(model, sp, httpIndex, member, raw)));
        }
        return ErlFunction.function(
                "decode_" + helperName,
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("null")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Map")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                                ErlRecord.record(helperName, fields.toArray(ErlRecordField[]::new)))));
    }

    private static ErlFunction structureEncode(
            Model model,
            HttpBindingIndex httpIndex,
            StructureShape structure,
            SymbolProvider sp,
            String helperName) {
        List<ErlMapEntry> entries = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            String wireKey = ErlangJsonCodecSupport.jsonKey(member);
            entries.add(ErlMapEntry.entry(
                    ErlBinary.binary(wireKey),
                    encodeFieldValueFromRecord(model, sp, httpIndex, structure, member, "Record")));
        }
        return ErlFunction.function(
                "encode_" + helperName,
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Record")),
                                ErlCall.call(
                                        "maps",
                                        "filter",
                                        ErlFun.fun(ErlClause.clause(
                                                List.of(
                                                        ErlVarPattern.varPattern("_"),
                                                        ErlVarPattern.varPattern("V")),
                                                ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                                        ErlMap.map(entries.toArray(ErlMapEntry[]::new))))));
    }

    private static ErlExpr decodeFieldValue(
            Model model,
            SymbolProvider sp,
            HttpBindingIndex httpIndex,
            MemberShape member,
            ErlExpr raw) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof EnumShape || target instanceof IntEnumShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("decode_" + helperName, raw);
        }
        if (target instanceof UnionShape union
                && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("decode_" + helperName, raw);
        }
        if (target instanceof StructureShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("decode_" + helperName, raw);
        }
        if (target instanceof TimestampShape) {
            String decodeHelper = timestampDecodeHelper(httpIndex, member);
            return ErlCallLocal.callLocal(decodeHelper, raw);
        }
        if (target instanceof ListShape listShape) {
            Shape element = model.expectShape(listShape.getMember().getTarget());
            if (element instanceof StructureShape) {
                String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
                return ErlCallLocal.callLocal("decode_" + helperName + "_list", raw);
            }
            String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
            return ErlCallLocal.callLocal(helper, raw);
        }
        if (target instanceof MapShape) {
            if (target.hasTrait(SparseTrait.class)) {
                return ErlCallLocal.callLocal("decode_sparse_map", raw);
            }
            return raw;
        }
        return raw;
    }

    private static ErlExpr encodeFieldValueFromRecord(
            Model model,
            SymbolProvider sp,
            HttpBindingIndex httpIndex,
            StructureShape parent,
            MemberShape member,
            String recordVar) {
        String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
        String recordName = ErlangJsonCodecSupport.structureHelperName(sp, parent);
        ErlExpr binding = ErlRecordAccess.recordAccess(ErlVar.var(recordVar), recordName, fieldName);
        return encodeFieldValue(model, sp, httpIndex, member, binding);
    }

    private static ErlExpr encodeFieldValue(
            Model model,
            SymbolProvider sp,
            HttpBindingIndex httpIndex,
            MemberShape member,
            ErlExpr binding) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof EnumShape || target instanceof IntEnumShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("encode_" + helperName, binding);
        }
        if (target instanceof UnionShape union
                && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("encode_" + helperName, binding);
        }
        if (target instanceof StructureShape) {
            String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
            return ErlCallLocal.callLocal("encode_" + helperName, binding);
        }
        if (target instanceof TimestampShape) {
            String encodeHelper = timestampEncodeHelper(httpIndex, member);
            return ErlCallLocal.callLocal(encodeHelper, binding);
        }
        if (target instanceof ListShape listShape) {
            Shape element = model.expectShape(listShape.getMember().getTarget());
            if (element instanceof StructureShape) {
                String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
                return ErlCallLocal.callLocal("encode_" + helperName + "_list", binding);
            }
            if (target.hasTrait(SparseTrait.class)) {
                return ErlCallLocal.callLocal("encode_sparse_list", binding);
            }
            return binding;
        }
        if (target instanceof MapShape) {
            if (target.hasTrait(SparseTrait.class)) {
                return ErlCallLocal.callLocal("encode_sparse_map", binding);
            }
            return binding;
        }
        return binding;
    }

    private static String timestampEncodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
        var fmt = httpIndex.determineTimestampFormat(
                member,
                software.amazon.smithy.model.knowledge.HttpBinding.Location.DOCUMENT,
                software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
        return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "encode_timestamp_epoch_seconds"
                : "encode_timestamp_date_time";
    }

    private static String timestampDecodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
        var fmt = httpIndex.determineTimestampFormat(
                member,
                software.amazon.smithy.model.knowledge.HttpBinding.Location.DOCUMENT,
                software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
        return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
                ? "decode_timestamp_epoch_seconds"
                : "decode_timestamp_date_time";
    }
}
