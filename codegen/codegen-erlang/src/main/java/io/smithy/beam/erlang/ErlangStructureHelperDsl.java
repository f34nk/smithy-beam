package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.Fun;
import io.beam.dsl.erlang.FunClause;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.InfixExpr;
import io.beam.dsl.erlang.IsTypeGuard;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MapEntry;
import io.beam.dsl.erlang.MapExpr;
import io.beam.dsl.erlang.RecordExpr;
import io.beam.dsl.erlang.RecordField;
import io.beam.dsl.erlang.RecordFieldAccessExpr;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
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

final class ErlangStructureHelperDsl {
  private ErlangStructureHelperDsl() {}

  static List<Function> structureDecodeEncode(
      Model model, HttpBindingIndex httpIndex, StructureShape structure, SymbolProvider sp) {
    String helperName = ErlangJsonCodecSupport.structureHelperName(sp, structure);
    return List.of(
        structureDecode(model, httpIndex, structure, sp, helperName),
        structureEncode(model, httpIndex, structure, sp, helperName));
  }

  private static Function structureDecode(
      Model model,
      HttpBindingIndex httpIndex,
      StructureShape structure,
      SymbolProvider sp,
      String helperName) {
    List<RecordField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      String wireKey = ErlangJsonCodecSupport.jsonKey(member);
      Expression raw =
          ErlangCodecHelperDsl.mapsGetDefault(
              BinaryExpr.of(wireKey), Variable.of("Map"), AtomExpr.of("undefined"));
      fields.add(RecordField.of(fieldName, decodeFieldValue(model, sp, httpIndex, member, raw)));
    }
    return Function.of(
        "decode_" + helperName,
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                RecordExpr.of(helperName, fields))));
  }

  private static Function structureEncode(
      Model model,
      HttpBindingIndex httpIndex,
      StructureShape structure,
      SymbolProvider sp,
      String helperName) {
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = ErlangJsonCodecSupport.jsonKey(member);
      entries.add(
          MapEntry.of(
              BinaryExpr.of(wireKey),
              encodeFieldValueFromRecord(model, sp, httpIndex, structure, member, "Record")));
    }
    return Function.of(
        "encode_" + helperName,
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Record")),
                RemoteCallExpr.of(
                    "maps",
                    "filter",
                    List.of(
                        Fun.of(
                            List.of(
                                FunClause.of(
                                    List.of(WildcardPattern.of(), VariablePattern.of("V")),
                                    InfixExpr.of(
                                        Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                        MapExpr.of(entries))))));
  }

  private static Expression decodeFieldValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("decode_" + helperName, List.of(raw));
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("decode_" + helperName, List.of(raw));
    }
    if (target instanceof StructureShape) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("decode_" + helperName, List.of(raw));
    }
    if (target instanceof TimestampShape) {
      String decodeHelper = timestampDecodeHelper(httpIndex, member);
      return LocalCallExpr.of(decodeHelper, List.of(raw));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
        return LocalCallExpr.of("decode_" + helperName + "_list", List.of(raw));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
        return LocalCallExpr.of("decode_" + helperName + "_list", List.of(raw));
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return LocalCallExpr.of(helper, List.of(raw));
    }
    if (target instanceof MapShape mapShape) {
      return ErlangMapHelperDsl.mapDecodeExpr(model, sp, httpIndex, mapShape, raw);
    }
    return raw;
  }

  private static Expression encodeFieldValueFromRecord(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      StructureShape parent,
      MemberShape member,
      String recordVar) {
    String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
    String recordName = ErlangJsonCodecSupport.structureHelperName(sp, parent);
    Expression binding = RecordFieldAccessExpr.of(Variable.of(recordVar), recordName, fieldName);
    return encodeFieldValue(model, sp, httpIndex, member, binding);
  }

  private static Expression encodeFieldValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression binding) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(binding));
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(binding));
    }
    if (target instanceof StructureShape) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(binding));
    }
    if (target instanceof TimestampShape) {
      String encodeHelper = timestampEncodeHelper(httpIndex, member);
      return LocalCallExpr.of(encodeHelper, List.of(binding));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
        return LocalCallExpr.of("encode_" + helperName + "_list", List.of(binding));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
        return LocalCallExpr.of("encode_" + helperName + "_list", List.of(binding));
      }
      if (target.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("encode_sparse_list", List.of(binding));
      }
      return binding;
    }
    if (target instanceof MapShape mapShape) {
      return ErlangMapHelperDsl.mapEncodeExpr(model, sp, httpIndex, mapShape, binding);
    }
    return binding;
  }

  private static String timestampEncodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
    var fmt =
        httpIndex.determineTimestampFormat(
            member,
            software.amazon.smithy.model.knowledge.HttpBinding.Location.DOCUMENT,
            software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
    return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "encode_timestamp_epoch_seconds"
        : "encode_timestamp_date_time";
  }

  private static String timestampDecodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
    var fmt =
        httpIndex.determineTimestampFormat(
            member,
            software.amazon.smithy.model.knowledge.HttpBinding.Location.DOCUMENT,
            software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
    return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "decode_timestamp_epoch_seconds"
        : "decode_timestamp_date_time";
  }
}
