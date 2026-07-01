package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
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

final class ElixirMapHelperIr {
  private ElixirMapHelperIr() {}

  static List<ExFunction> mapDecodeEncode(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp) {
    if (!mapNeedsTypedHelper(model, map)) {
      return List.of();
    }
    String helperName = mapHelperName(map);
    return List.of(
        buildDecodeMap(model, httpIndex, map, sp, helperName),
        buildEncodeMap(model, httpIndex, map, sp, helperName));
  }

  static ExExpr mapEncodeExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MapShape map, ExExpr binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return ExCallLocal.callLocal("encode_" + mapHelperName(map), binding);
    }
    if (map.hasTrait(SparseTrait.class)) {
      return ExCallLocal.callLocal("encode_sparse_map", binding);
    }
    return binding;
  }

  static ExExpr mapDecodeExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MapShape map, ExExpr binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return ExCallLocal.callLocal("decode_" + mapHelperName(map), binding);
    }
    if (map.hasTrait(SparseTrait.class)) {
      return ExCallLocal.callLocal("decode_sparse_map", binding);
    }
    return binding;
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

  private static ExFunction buildEncodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    return ExFunction.defpFunction(
        "encode_" + helperName,
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                ExCall.call(
                    "Map",
                    "new",
                    ExVar.var("map"),
                    encodeMapTransform(model, httpIndex, map, sp)))));
  }

  private static ExFunction buildDecodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    return ExFunction.defpFunction(
        "decode_" + helperName,
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                ExCall.call(
                    "Map",
                    "new",
                    ExVar.var("map"),
                    decodeMapTransform(model, httpIndex, map, sp)))));
  }

  private static ExAnonymousFn encodeMapTransform(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp) {
    MemberShape keyMember = map.getKey();
    MemberShape valueMember = map.getValue();
    boolean sparse = map.hasTrait(SparseTrait.class);
    List<ExClause> clauses = new ArrayList<>();
    if (sparse) {
      clauses.add(
          ExClause.clause(
              List.of(ExTuplePattern.tuple(ExVarPattern.var("k"), ExNilPattern.nil())),
              ExTuple.tuple(
                  encodeMapKey(model, sp, httpIndex, keyMember, ExVar.var("k")),
                  ExAtom.atom("nil"))));
    }
    clauses.add(
        ExClause.clause(
            List.of(ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v"))),
            ExTuple.tuple(
                encodeMapKey(model, sp, httpIndex, keyMember, ExVar.var("k")),
                encodeMapValue(model, sp, httpIndex, valueMember, ExVar.var("v")))));
    return ExAnonymousFn.fn(clauses.toArray(ExClause[]::new));
  }

  private static ExAnonymousFn decodeMapTransform(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp) {
    MemberShape keyMember = map.getKey();
    MemberShape valueMember = map.getValue();
    boolean sparse = map.hasTrait(SparseTrait.class);
    List<ExClause> clauses = new ArrayList<>();
    if (sparse) {
      clauses.add(
          ExClause.clause(
              List.of(ExTuplePattern.tuple(ExVarPattern.var("k"), ExNilPattern.nil())),
              ExTuple.tuple(
                  decodeMapKey(model, sp, httpIndex, keyMember, ExVar.var("k")),
                  ExAtom.atom("nil"))));
    }
    clauses.add(
        ExClause.clause(
            List.of(ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v"))),
            ExTuple.tuple(
                decodeMapKey(model, sp, httpIndex, keyMember, ExVar.var("k")),
                decodeMapValue(model, sp, httpIndex, valueMember, ExVar.var("v")))));
    return ExAnonymousFn.fn(clauses.toArray(ExClause[]::new));
  }

  private static ExExpr encodeMapKey(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, ExExpr key) {
    return ElixirJsonCodecIr.encodeJsonExpr(model, sp, httpIndex, member, key);
  }

  private static ExExpr decodeMapKey(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, ExExpr key) {
    return ElixirJsonCodecIr.decodeJsonExpr(model, sp, httpIndex, member, key);
  }

  private static ExExpr encodeMapValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      ExExpr value) {
    return ElixirJsonCodecIr.encodeJsonExpr(model, sp, httpIndex, member, value);
  }

  private static ExExpr decodeMapValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      ExExpr value) {
    return ElixirJsonCodecIr.decodeJsonExpr(model, sp, httpIndex, member, value);
  }
}
