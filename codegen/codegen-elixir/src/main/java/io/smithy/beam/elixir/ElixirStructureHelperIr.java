package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
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
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class ElixirStructureHelperIr {
  private ElixirStructureHelperIr() {}

  static List<ExFunction> structureDecodeEncode(
      Model model, HttpBindingIndex httpIndex, StructureShape structure, SymbolProvider sp) {
    String helperName = helperName(structure);
    return List.of(
        structureDecode(model, httpIndex, structure, sp, helperName),
        structureEncode(model, httpIndex, structure, sp, helperName));
  }

  static List<ExFunction> structureListDecodeEncode(StructureShape structure) {
    String helperName = helperName(structure);
    return List.of(structureListDecode(helperName), structureListEncode(helperName));
  }

  private static ExFunction structureDecode(
      Model model,
      HttpBindingIndex httpIndex,
      StructureShape structure,
      SymbolProvider sp,
      String helperName) {
    String structName = sp.toSymbol(structure).getName();
    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String fieldName = fieldName(member);
      String wireKey = jsonKey(member);
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(fieldName),
              decodeFieldValue(
                  model,
                  sp,
                  httpIndex,
                  member,
                  ExCall.call(
                      "Map",
                      "get",
                      ExVar.var("map"),
                      ExString.string(wireKey)))));
    }
    return ExFunction.defpFunction(
        "decode_" + helperName,
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                ExStruct.struct("Types." + structName, fields))));
  }

  private static ExFunction structureEncode(
      Model model,
      HttpBindingIndex httpIndex,
      StructureShape structure,
      SymbolProvider sp,
      String helperName) {
    String structName = sp.toSymbol(structure).getName();
    List<ExMapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      entries.add(
          ExMapEntry.entry(
              ExString.string(wireKey),
              encodeFieldValueFromRecord(model, sp, httpIndex, structure, member, "record")));
    }
    return ExFunction.defpFunction(
        "encode_" + helperName,
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(
                    ExStructPattern.structFunctionHead(
                        "record", "Types." + structName, List.of())),
                ExPipeline.pipeline(
                    "_map",
                    new ExMap(entries),
                    ExCapturedBlock.capturedBlock("Enum.reject(fn {_k, v} -> is_nil(v) end)"),
                    ExCapturedBlock.capturedBlock("Map.new()")))));
  }

  private static ExFunction structureListDecode(String helperName) {
    return ExFunction.defpFunction(
        "decode_" + helperName + "_list",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("list")),
                List.of(ExGuard.guard("is_list", ExVar.var("list"))),
                ExCapturedBlock.capturedBlock(
                    "Enum.map(list, fn v -> decode_" + helperName + "(v) end)"))));
  }

  private static ExFunction structureListEncode(String helperName) {
    return ExFunction.defpFunction(
        "encode_" + helperName + "_list",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("list")),
                List.of(ExGuard.guard("is_list", ExVar.var("list"))),
                ExCapturedBlock.capturedBlock(
                    "Enum.map(list, fn v -> encode_" + helperName + "(v) end)"))));
  }

  private static io.smithy.beam.ir.elixir.ExExpr decodeFieldValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      io.smithy.beam.ir.elixir.ExExpr raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return ExCallLocal.callLocal("decode_" + helperName(target), raw);
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      return ExCallLocal.callLocal("decode_" + helperName(target), raw);
    }
    if (target instanceof StructureShape) {
      return ExCallLocal.callLocal("decode_" + helperName(target), raw);
    }
    if (target instanceof TimestampShape) {
      return ExCallLocal.callLocal(timestampDecodeHelper(httpIndex, member), raw);
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return ExCallLocal.callLocal("decode_" + helperName(element) + "_list", raw);
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return ExCallLocal.callLocal(helper, raw);
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return ExCallLocal.callLocal("decode_sparse_map", raw);
      }
      return raw;
    }
    return raw;
  }

  private static io.smithy.beam.ir.elixir.ExExpr encodeFieldValueFromRecord(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      StructureShape parent,
      MemberShape member,
      String recordVar) {
    String field = fieldName(member);
    return encodeFieldValue(
        model,
        sp,
        httpIndex,
        member,
        ExStructAccess.structAccess(ExVar.var(recordVar), field));
  }

  private static io.smithy.beam.ir.elixir.ExExpr encodeFieldValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      io.smithy.beam.ir.elixir.ExExpr binding) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return ExCallLocal.callLocal("encode_" + helperName(target), binding);
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      return ExCallLocal.callLocal("encode_" + helperName(target), binding);
    }
    if (target instanceof StructureShape) {
      return ExCallLocal.callLocal("encode_" + helperName(target), binding);
    }
    if (target instanceof TimestampShape) {
      return ExCallLocal.callLocal(timestampEncodeHelper(httpIndex, member), binding);
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return ExCallLocal.callLocal("encode_" + helperName(element) + "_list", binding);
      }
      if (target.hasTrait(SparseTrait.class)) {
        return ExCallLocal.callLocal("encode_sparse_list", binding);
      }
      return binding;
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return ExCallLocal.callLocal("encode_sparse_map", binding);
      }
      return binding;
    }
    return binding;
  }

  private static String timestampEncodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
    var fmt =
        httpIndex.determineTimestampFormat(
            member, HttpBinding.Location.DOCUMENT, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "encode_timestamp_epoch_seconds"
        : "encode_timestamp_date_time";
  }

  private static String timestampDecodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
    var fmt =
        httpIndex.determineTimestampFormat(
            member, HttpBinding.Location.DOCUMENT, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "decode_timestamp_epoch_seconds"
        : "decode_timestamp_date_time";
  }

  private static String helperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  private static String fieldName(MemberShape member) {
    return BeamNameUtils.toSnakeCase(member.getMemberName());
  }

  private static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }
}
