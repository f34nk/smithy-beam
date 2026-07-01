package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComprehensionGenerator;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
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

final class ErlangMapHelperIr {
  private ErlangMapHelperIr() {}

  static List<ErlFunction> mapDecodeEncode(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp) {
    if (!mapNeedsTypedHelper(model, map)) {
      return List.of();
    }
    String helperName = mapHelperName(map);
    return List.of(
        buildDecodeMap(model, httpIndex, map, sp, helperName),
        buildEncodeMap(model, httpIndex, map, sp, helperName));
  }

  static String mapEncodeExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MapShape map, String binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return "encode_" + mapHelperName(map) + "(" + binding + ")";
    }
    if (map.hasTrait(SparseTrait.class)) {
      return "encode_sparse_map(" + binding + ")";
    }
    return binding;
  }

  static ErlExpr mapEncodeExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MapShape map, ErlExpr binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return ErlCallLocal.callLocal("encode_" + mapHelperName(map), binding);
    }
    if (map.hasTrait(SparseTrait.class)) {
      return ErlCallLocal.callLocal("encode_sparse_map", binding);
    }
    return binding;
  }

  static String mapDecodeExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MapShape map, String binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return "decode_" + mapHelperName(map) + "(" + binding + ")";
    }
    if (map.hasTrait(SparseTrait.class)) {
      return "decode_sparse_map(" + binding + ")";
    }
    return binding;
  }

  static ErlExpr mapDecodeExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MapShape map, ErlExpr binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return ErlCallLocal.callLocal("decode_" + mapHelperName(map), binding);
    }
    if (map.hasTrait(SparseTrait.class)) {
      return ErlCallLocal.callLocal("decode_sparse_map", binding);
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

  private static ErlFunction buildEncodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    return ErlFunction.function(
        "encode_" + helperName,
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Map")),
                List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                transformMap(model, httpIndex, map, sp, true))));
  }

  private static ErlFunction buildDecodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    return ErlFunction.function(
        "decode_" + helperName,
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
            ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("null")), ErlAtom.atom("undefined")),
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Map")),
                List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                transformMap(model, httpIndex, map, sp, false))));
  }

  private static ErlCall transformMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, boolean encode) {
    MemberShape keyMember = map.getKey();
    MemberShape valueMember = map.getValue();
    boolean sparse = map.hasTrait(SparseTrait.class);
    ErlExpr entryExpr =
        sparse
            ? sparseMapEntry(model, httpIndex, keyMember, valueMember, sp, encode)
            : mapEntry(model, httpIndex, keyMember, valueMember, sp, encode);
    return ErlCall.call(
        "maps",
        "from_list",
        ErlListComprehension.comprehensionQualifiers(
            entryExpr,
            List.of(
                new ErlComprehensionGenerator(
                    ErlTuplePattern.tuplePattern(
                        ErlVarPattern.varPattern("K"), ErlVarPattern.varPattern("V")),
                    ErlCall.call("maps", "to_list", ErlVar.var("Map"))))));
  }

  private static ErlTuple mapEntry(
      Model model,
      HttpBindingIndex httpIndex,
      MemberShape keyMember,
      MemberShape valueMember,
      SymbolProvider sp,
      boolean encode) {
    return ErlTuple.tuple(
        mapKey(model, sp, httpIndex, keyMember, encode),
        mapValue(model, sp, httpIndex, valueMember, encode));
  }

  private static ErlCase sparseMapEntry(
      Model model,
      HttpBindingIndex httpIndex,
      MemberShape keyMember,
      MemberShape valueMember,
      SymbolProvider sp,
      boolean encode) {
    if (encode) {
      return ErlCase.caseExpr(
          ErlVar.var("V"),
          ErlClause.clause(
              List.of(ErlAtomPattern.atomPattern("undefined")),
              ErlTuple.tuple(mapKey(model, sp, httpIndex, keyMember, true), ErlAtom.atom("null"))),
          ErlClause.clause(
              List.of(ErlVarPattern.varPattern("_")),
              mapEntry(model, httpIndex, keyMember, valueMember, sp, true)));
    }
    return ErlCase.caseExpr(
        ErlVar.var("V"),
        ErlClause.clause(
            List.of(ErlAtomPattern.atomPattern("null")),
            ErlTuple.tuple(
                mapKey(model, sp, httpIndex, keyMember, false), ErlAtom.atom("undefined"))),
        ErlClause.clause(
            List.of(ErlVarPattern.varPattern("_")),
            mapEntry(model, httpIndex, keyMember, valueMember, sp, false)));
  }

  private static ErlExpr mapKey(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      boolean encode) {
    if (encode) {
      return ErlangJsonCodecSupport.encodeJsonExpr(model, sp, httpIndex, member, "K");
    }
    return ErlangJsonCodecSupport.decodeJsonExpr(model, sp, httpIndex, member, ErlVar.var("K"));
  }

  private static ErlExpr mapValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      boolean encode) {
    if (encode) {
      return ErlangJsonCodecSupport.encodeJsonExpr(model, sp, httpIndex, member, "V");
    }
    return ErlangJsonCodecSupport.decodeJsonExpr(model, sp, httpIndex, member, ErlVar.var("V"));
  }
}
