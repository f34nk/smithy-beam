package io.smithy.beam.elixir;

import io.beam.ir.elixir.AnonFun;
import io.beam.ir.elixir.AnonFunClause;
import io.beam.ir.elixir.AssignPattern;
import io.beam.ir.elixir.DotCallExpr;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.IsTypeGuard;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.MapEntry;
import io.beam.ir.elixir.MapExpr;
import io.beam.ir.elixir.NilExpr;
import io.beam.ir.elixir.NilPattern;
import io.beam.ir.elixir.Pattern;
import io.beam.ir.elixir.PipeExpr;
import io.beam.ir.elixir.PipeStep;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.StringExpr;
import io.beam.ir.elixir.StructExpr;
import io.beam.ir.elixir.StructField;
import io.beam.ir.elixir.StructPattern;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
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

  static List<Function> structureDecodeEncode(
      Model model, HttpBindingIndex httpIndex, StructureShape structure, SymbolProvider sp) {
    String helperName = helperName(structure);
    List<Function> functions = new ArrayList<>();
    functions.addAll(buildStructureDecode(model, httpIndex, structure, sp, helperName));
    functions.addAll(buildStructureEncode(model, httpIndex, structure, sp, helperName));
    return functions;
  }

  static List<Function> structureListDecodeEncode(StructureShape structure) {
    String helperName = helperName(structure);
    List<Function> functions = new ArrayList<>();
    functions.addAll(structureListDecode(helperName));
    functions.addAll(structureListEncode(helperName));
    return functions;
  }

  private static List<Function> buildStructureDecode(
      Model model,
      HttpBindingIndex httpIndex,
      StructureShape structure,
      SymbolProvider sp,
      String helperName) {
    String structName = sp.toSymbol(structure).getName();
    List<StructField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String fieldName = fieldName(member);
      String wireKey = jsonKey(member);
      fields.add(
          StructField.of(
              fieldName,
              decodeFieldValue(
                  model,
                  sp,
                  httpIndex,
                  member,
                  RemoteCallExpr.of(
                      "Map", "get", List.of(Variable.of("map"), StringExpr.of(wireKey))))));
    }
    String name = "decode_" + helperName;
    return List.of(
        defp(name, List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            name,
            List.of(VariablePattern.of("map")),
            IsTypeGuard.of("is_map", "map"),
            StructExpr.of("Types." + structName, fields),
            false));
  }

  private static List<Function> buildStructureEncode(
      Model model,
      HttpBindingIndex httpIndex,
      StructureShape structure,
      SymbolProvider sp,
      String helperName) {
    String structName = sp.toSymbol(structure).getName();
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      entries.add(
          MapEntry.stringKey(
              wireKey,
              encodeFieldValueFromRecord(model, sp, httpIndex, structure, member, "record")));
    }
    String name = "encode_" + helperName;
    return List.of(
        defp(name, List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            name,
            List.of(AssignPattern.of("record", StructPattern.of("Types." + structName, List.of()))),
            new PipeExpr(
                MapExpr.of(entries),
                List.of(
                    new PipeStep(
                        RemoteCallExpr.of(
                            "Enum",
                            "reject",
                            List.of(
                                new AnonFun(
                                    List.of(
                                        AnonFunClause.of(
                                            List.of(
                                                TuplePattern.of(
                                                    List.of(
                                                        VariablePattern.of("_k"),
                                                        VariablePattern.of("v")))),
                                            LocalCallExpr.of(
                                                "is_nil", List.of(Variable.of("v")))))))),
                        List.of()),
                    new PipeStep(RemoteCallExpr.of("Map", "new", List.of()), List.of()))),
            false));
  }

  private static List<Function> structureListDecode(String helperName) {
    String name = "decode_" + helperName + "_list";
    return List.of(
        defp(name, List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            name,
            List.of(VariablePattern.of("list")),
            IsTypeGuard.of("is_list", "list"),
            RemoteCallExpr.of(
                "Enum",
                "map",
                List.of(
                    Variable.of("list"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(
                                List.of(VariablePattern.of("v")),
                                LocalCallExpr.of(
                                    "decode_" + helperName, List.of(Variable.of("v")))))))),
            false));
  }

  private static List<Function> structureListEncode(String helperName) {
    String name = "encode_" + helperName + "_list";
    return List.of(
        defp(name, List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            name,
            List.of(VariablePattern.of("list")),
            IsTypeGuard.of("is_list", "list"),
            RemoteCallExpr.of(
                "Enum",
                "map",
                List.of(
                    Variable.of("list"),
                    new AnonFun(
                        List.of(
                            AnonFunClause.of(
                                List.of(VariablePattern.of("v")),
                                LocalCallExpr.of(
                                    "encode_" + helperName, List.of(Variable.of("v")))))))),
            false));
  }

  private static Expression decodeFieldValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return LocalCallExpr.of("decode_" + helperName(target), List.of(raw));
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      return LocalCallExpr.of("decode_" + helperName(target), List.of(raw));
    }
    if (target instanceof StructureShape) {
      return LocalCallExpr.of("decode_" + helperName(target), List.of(raw));
    }
    if (target instanceof TimestampShape) {
      return LocalCallExpr.of(timestampDecodeHelper(httpIndex, member), List.of(raw));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return LocalCallExpr.of("decode_" + helperName(element) + "_list", List.of(raw));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        return LocalCallExpr.of("decode_" + helperName(element) + "_list", List.of(raw));
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return LocalCallExpr.of(helper, List.of(raw));
    }
    if (target instanceof MapShape mapShape) {
      if (ElixirMapHelperIr.mapNeedsTypedHelper(model, mapShape)) {
        return LocalCallExpr.of(
            "decode_" + ElixirMapHelperIr.mapHelperName(mapShape), List.of(raw));
      }
      if (mapShape.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("decode_sparse_map", List.of(raw));
      }
      return raw;
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
    String field = fieldName(member);
    return encodeFieldValue(
        model, sp, httpIndex, member, new DotCallExpr(Variable.of(recordVar), field, List.of()));
  }

  private static Expression encodeFieldValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression binding) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return LocalCallExpr.of("encode_" + helperName(target), List.of(binding));
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      return LocalCallExpr.of("encode_" + helperName(target), List.of(binding));
    }
    if (target instanceof StructureShape) {
      return LocalCallExpr.of("encode_" + helperName(target), List.of(binding));
    }
    if (target instanceof TimestampShape) {
      return LocalCallExpr.of(timestampEncodeHelper(httpIndex, member), List.of(binding));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return LocalCallExpr.of("encode_" + helperName(element) + "_list", List.of(binding));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        return LocalCallExpr.of("encode_" + helperName(element) + "_list", List.of(binding));
      }
      if (target.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("encode_sparse_list", List.of(binding));
      }
      return binding;
    }
    if (target instanceof MapShape mapShape) {
      if (ElixirMapHelperIr.mapNeedsTypedHelper(model, mapShape)) {
        return LocalCallExpr.of(
            "encode_" + ElixirMapHelperIr.mapHelperName(mapShape), List.of(binding));
      }
      if (mapShape.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("encode_sparse_map", List.of(binding));
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

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, IsTypeGuard guard, Expression body, boolean oneLiner) {
    return new Function(
        name, true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
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
