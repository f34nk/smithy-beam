package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.IsTypeGuard;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.ListComprehensionGenerator;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import io.smithy.beam.core.BeamNameUtils;
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

  static List<Function> mapDecodeEncode(
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

  static Expression mapEncodeExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MapShape map,
      Expression binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return LocalCallExpr.of("encode_" + mapHelperName(map), List.of(binding));
    }
    if (map.hasTrait(SparseTrait.class)) {
      return LocalCallExpr.of("encode_sparse_map", List.of(binding));
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

  static Expression mapDecodeExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MapShape map,
      Expression binding) {
    if (mapNeedsTypedHelper(model, map)) {
      return LocalCallExpr.of("decode_" + mapHelperName(map), List.of(binding));
    }
    if (map.hasTrait(SparseTrait.class)) {
      return LocalCallExpr.of("decode_sparse_map", List.of(binding));
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

  private static Function buildEncodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    return Function.of(
        "encode_" + helperName,
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                transformMap(model, httpIndex, map, sp, true))));
  }

  private static Function buildDecodeMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, String helperName) {
    return Function.of(
        "decode_" + helperName,
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                transformMap(model, httpIndex, map, sp, false))));
  }

  private static RemoteCallExpr transformMap(
      Model model, HttpBindingIndex httpIndex, MapShape map, SymbolProvider sp, boolean encode) {
    MemberShape keyMember = map.getKey();
    MemberShape valueMember = map.getValue();
    boolean sparse = map.hasTrait(SparseTrait.class);
    Expression entryExpr =
        sparse
            ? sparseMapEntry(model, httpIndex, keyMember, valueMember, sp, encode)
            : mapEntry(model, httpIndex, keyMember, valueMember, sp, encode);
    return RemoteCallExpr.of(
        "maps",
        "from_list",
        List.of(
            ListComprehensionExpr.of(
                entryExpr,
                List.of(
                    ListComprehensionGenerator.of(
                        TuplePattern.of(List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                        RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Map"))))))));
  }

  private static TupleExpr mapEntry(
      Model model,
      HttpBindingIndex httpIndex,
      MemberShape keyMember,
      MemberShape valueMember,
      SymbolProvider sp,
      boolean encode) {
    return TupleExpr.of(
        List.of(
            mapKey(model, sp, httpIndex, keyMember, encode),
            mapValue(model, sp, httpIndex, valueMember, encode)));
  }

  private static CaseExpr sparseMapEntry(
      Model model,
      HttpBindingIndex httpIndex,
      MemberShape keyMember,
      MemberShape valueMember,
      SymbolProvider sp,
      boolean encode) {
    if (encode) {
      return CaseExpr.of(
          Variable.of("V"),
          List.of(
              Clause.of(
                  AtomPattern.of("undefined"),
                  TupleExpr.of(
                      List.of(mapKey(model, sp, httpIndex, keyMember, true), AtomExpr.of("null")))),
              Clause.of(
                  WildcardPattern.of(),
                  mapEntry(model, httpIndex, keyMember, valueMember, sp, true))));
    }
    return CaseExpr.of(
        Variable.of("V"),
        List.of(
            Clause.of(
                AtomPattern.of("null"),
                TupleExpr.of(
                    List.of(
                        mapKey(model, sp, httpIndex, keyMember, false), AtomExpr.of("undefined")))),
            Clause.of(
                WildcardPattern.of(),
                mapEntry(model, httpIndex, keyMember, valueMember, sp, false))));
  }

  private static Expression mapKey(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      boolean encode) {
    if (encode) {
      return encodeMemberExpr(model, sp, httpIndex, member, "K");
    }
    return decodeMemberExpr(model, sp, httpIndex, member, Variable.of("K"));
  }

  private static Expression mapValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      boolean encode) {
    if (encode) {
      return encodeMemberExpr(model, sp, httpIndex, member, "V");
    }
    return decodeMemberExpr(model, sp, httpIndex, member, Variable.of("V"));
  }

  private static Expression decodeMemberExpr(
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
    if (target instanceof UnionShape) {
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
      return mapDecodeExpr(model, sp, httpIndex, mapShape, raw);
    }
    return raw;
  }

  private static Expression encodeMemberExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String bindingVar) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof UnionShape) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof StructureShape) {
      String helperName = ErlangJsonCodecSupport.structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof TimestampShape) {
      String encodeHelper = timestampEncodeHelper(httpIndex, member);
      return LocalCallExpr.of(encodeHelper, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
        return LocalCallExpr.of("encode_" + helperName + "_list", List.of(Variable.of(bindingVar)));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        String helperName = ErlangJsonCodecSupport.structureHelperName(sp, element);
        return LocalCallExpr.of("encode_" + helperName + "_list", List.of(Variable.of(bindingVar)));
      }
      if (target.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("encode_sparse_list", List.of(Variable.of(bindingVar)));
      }
      return Variable.of(bindingVar);
    }
    if (target instanceof MapShape mapShape) {
      return mapEncodeExpr(model, sp, httpIndex, mapShape, Variable.of(bindingVar));
    }
    return Variable.of(bindingVar);
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
