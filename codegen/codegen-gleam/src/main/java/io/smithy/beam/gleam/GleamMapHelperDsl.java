package io.smithy.beam.gleam;

import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.LocalCallExpr;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.RawExpr;
import io.beam.lang.gleam.RemoteCallExpr;
import io.beam.lang.gleam.Variable;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class GleamMapHelperDsl {

  private GleamMapHelperDsl() {}

  static boolean mapNeedsTypedHelper(Model model, MapShape map) {
    Shape key = model.expectShape(map.getKey().getTarget());
    Shape value = model.expectShape(map.getValue().getTarget());
    return shapeNeedsWireCoding(model, key) || shapeNeedsWireCoding(model, value);
  }

  static List<Function> mapHelpers(
      Model model, MapShape map, SymbolProvider sp, String typesModule) {
    if (!mapNeedsTypedHelper(model, map)) {
      return List.of();
    }
    List<Function> functions = new ArrayList<>();
    String type = GleamCodecNames.qualifiedType(typesModule, sp, map);
    String valueDecoder = GleamJsonMemberCoding.shapeDecoderSource(model, map.getValue(), sp);
    String valueToJson =
        io.beam.lang.gleam.GleamRenderer.render(
            GleamJsonMemberCoding.encodeFnPublic(
                model, model.expectShape(map.getValue().getTarget()), sp));

    functions.add(
        Function.pub(
            GleamCodecNames.toJsonName(map),
            List.of(Param.of("value", type)),
            "json.Json",
            RawExpr.of("json.dict(value, fn(k) { k }, " + valueToJson + ")")));
    functions.add(
        Function.pub(
            GleamCodecNames.decoderName(map),
            List.of(),
            "decode.Decoder(" + type + ")",
            RawExpr.of("decode.dict(decode.string, " + valueDecoder + ")")));
    functions.add(
        Function.pub(
            GleamCodecNames.jsonToName(map),
            List.of(Param.of("data", "dynamic.Dynamic")),
            "Result(" + type + ", List(decode.DecodeError))",
            RemoteCallExpr.of(
                "decode",
                "run",
                List.of(
                    Variable.of("data"),
                    LocalCallExpr.of(GleamCodecNames.decoderName(map), List.of())))));
    return functions;
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
}
