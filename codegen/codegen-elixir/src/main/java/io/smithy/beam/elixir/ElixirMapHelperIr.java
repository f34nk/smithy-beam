package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.IsTypeGuard;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.NilExpr;
import io.beam.dsl.elixir.NilPattern;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
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
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class ElixirMapHelperIr {
  private ElixirMapHelperIr() {}

  static List<Function> mapDecodeEncode(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp) {
    if (!mapNeedsTypedHelper(model, map)) {
      return List.of();
    }
    String helperName = mapHelperName(map);
    List<Function> functions = new ArrayList<>();
    functions.addAll(buildDecodeMap(model, httpIndex, map, sp, helperName));
    functions.addAll(buildEncodeMap(model, httpIndex, map, sp, helperName));
    return functions;
  }

  static boolean mapNeedsTypedHelper(Model model, MapShape map) {
    Shape key = model.expectShape(map.getKey().getTarget());
    Shape value = model.expectShape(map.getValue().getTarget());
    return shapeNeedsWireCoding(model, key) || shapeNeedsWireCoding(model, value);
  }

  static String mapHelperName(MapShape map) {
    return BeamNameUtils.toSnakeCase(map.getId().getName());
  }

  private static boolean shapeNeedsWireCoding(Model model, Shape shape) {
    if (shape instanceof EnumShape || shape instanceof IntEnumShape) {
      return true;
    }
    if (shape instanceof StructureShape || shape instanceof UnionShape) {
      return true;
    }
    if (shape instanceof TimestampShape) {
      return true;
    }
    if (shape instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      return element instanceof StructureShape;
    }
    if (shape instanceof MapShape mapShape) {
      return mapNeedsTypedHelper(model, mapShape);
    }
    return false;
  }

  private static List<Function> buildEncodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    String name = "encode_" + helperName;
    Expression mapBody = mapTransformBody(model, httpIndex, map, sp, true);
    return List.of(
        Function.of(
            name,
            true,
            List.of(FunctionHead.of(List.of(NilPattern.of()))),
            NilExpr.of(),
            null,
            null,
            true),
        Function.of(
            name,
            true,
            List.of(
                FunctionHead.of(
                    List.of(VariablePattern.of("map")), IsTypeGuard.of("is_map", "map"))),
            mapBody,
            null,
            null,
            false));
  }

  private static List<Function> buildDecodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    String name = "decode_" + helperName;
    Expression mapBody = mapTransformBody(model, httpIndex, map, sp, false);
    return List.of(
        Function.of(
            name,
            true,
            List.of(FunctionHead.of(List.of(NilPattern.of()))),
            NilExpr.of(),
            null,
            null,
            true),
        Function.of(
            name,
            true,
            List.of(
                FunctionHead.of(
                    List.of(VariablePattern.of("map")), IsTypeGuard.of("is_map", "map"))),
            mapBody,
            null,
            null,
            false));
  }

  private static Expression mapTransformBody(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, boolean encode) {
    return RemoteCallExpr.of(
        "Map",
        "new",
        List.of(
            Variable.of("map"),
            AnonFun.of(mapTransformClauses(model, httpIndex, map, sp, encode))));
  }

  private static List<AnonFunClause> mapTransformClauses(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, boolean encode) {
    MemberShape keyMember = map.getKey();
    MemberShape valueMember = map.getValue();
    boolean sparse = map.hasTrait(SparseTrait.class);
    List<AnonFunClause> clauses = new ArrayList<>();
    if (sparse) {
      clauses.add(
          AnonFunClause.of(
              List.of(TuplePattern.of(List.of(VariablePattern.of("k"), NilPattern.of()))),
              TupleExpr.of(
                  List.of(
                      wireExpr(model, sp, httpIndex, keyMember, Variable.of("k"), encode),
                      NilExpr.of()))));
    }
    clauses.add(
        AnonFunClause.of(
            List.of(TuplePattern.of(List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
            TupleExpr.of(
                List.of(
                    wireExpr(model, sp, httpIndex, keyMember, Variable.of("k"), encode),
                    wireExpr(model, sp, httpIndex, valueMember, Variable.of("v"), encode)))));
    return clauses;
  }

  private static Expression wireExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression binding,
      boolean encode) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return LocalCallExpr.of(
          (encode ? "encode_" : "decode_") + helperName(target), List.of(binding));
    }
    if (target instanceof UnionShape) {
      return LocalCallExpr.of(
          (encode ? "encode_" : "decode_") + helperName(target), List.of(binding));
    }
    if (target instanceof StructureShape) {
      return LocalCallExpr.of(
          (encode ? "encode_" : "decode_") + helperName(target), List.of(binding));
    }
    if (target instanceof TimestampShape) {
      return LocalCallExpr.of(timestampHelper(httpIndex, member, encode), List.of(binding));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return LocalCallExpr.of(
            (encode ? "encode_" : "decode_") + helperName(element) + "_list", List.of(binding));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        return LocalCallExpr.of(
            (encode ? "encode_" : "decode_") + helperName(element) + "_list", List.of(binding));
      }
      if (encode && !target.hasTrait(SparseTrait.class)) {
        return binding;
      }
      String helper =
          target.hasTrait(SparseTrait.class)
              ? (encode ? "encode_sparse_list" : "decode_sparse_list")
              : "decode_list";
      return LocalCallExpr.of(helper, List.of(binding));
    }
    if (target instanceof MapShape mapShape) {
      if (mapNeedsTypedHelper(model, mapShape)) {
        return LocalCallExpr.of(
            (encode ? "encode_" : "decode_") + mapHelperName(mapShape), List.of(binding));
      }
      if (mapShape.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of(
            encode ? "encode_sparse_map" : "decode_sparse_map", List.of(binding));
      }
      return binding;
    }
    return binding;
  }

  private static String helperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  private static String timestampHelper(
      HttpBindingIndex httpIndex, MemberShape member, boolean encode) {
    TimestampFormatTrait.Format fmt =
        httpIndex.determineTimestampFormat(
            member, HttpBinding.Location.DOCUMENT, TimestampFormatTrait.Format.DATE_TIME);
    if (encode) {
      return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
          ? "encode_timestamp_epoch_seconds"
          : "encode_timestamp_date_time";
    }
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "decode_timestamp_epoch_seconds"
        : "decode_timestamp_date_time";
  }
}
