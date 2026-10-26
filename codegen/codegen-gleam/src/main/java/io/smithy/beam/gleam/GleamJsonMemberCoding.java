package io.smithy.beam.gleam;

import io.beam.lang.gleam.Expression;
import io.beam.lang.gleam.LocalCallExpr;
import io.beam.lang.gleam.RawExpr;
import io.beam.lang.gleam.RemoteCallExpr;
import io.beam.lang.gleam.Variable;
import io.smithy.beam.core.BeamMemberNullability;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * First-cut member encode/decode expressions for restJson1 JSON object codecs. Complex wire formats
 * (timestamp variants, blob/base64, document encode) are intentionally shallow; see plan G4.4b.
 */
final class GleamJsonMemberCoding {

  private GleamJsonMemberCoding() {}

  static Expression memberDecoder(
      Model model, StructureShape container, MemberShape member, SymbolProvider sp) {
    Shape target = model.expectShape(member.getTarget());
    Expression inner = shapeDecoder(model, target, sp);
    NullableIndex index = NullableIndex.of(model);
    if (!BeamMemberNullability.isMemberNullable(index, container, member)) {
      return RemoteCallExpr.of(
          "decode", "field", List.of(StringExprOf(GleamCodecNames.jsonKey(member)), inner));
    }
    // optional_field(key, default, decoder, next) -- default None when the key is absent.
    return RemoteCallExpr.of(
        "decode",
        "optional_field",
        List.of(
            StringExprOf(GleamCodecNames.jsonKey(member)),
            Variable.of("option.None"),
            RemoteCallExpr.of("decode", "optional", List.of(inner))));
  }

  static Expression encodeMemberValue(
      Model model,
      StructureShape container,
      MemberShape member,
      SymbolProvider sp,
      Expression valueExpr) {
    Shape target = model.expectShape(member.getTarget());
    NullableIndex index = NullableIndex.of(model);
    if (!BeamMemberNullability.isMemberNullable(index, container, member)) {
      return encodeShape(model, target, sp, valueExpr);
    }
    return RemoteCallExpr.of(
        "json", "nullable", List.of(valueExpr, encodeFn(model, target, sp)));
  }

  static Expression encodeRequiredValue(
      Model model, MemberShape member, SymbolProvider sp, Expression valueExpr) {
    return encodeShape(model, model.expectShape(member.getTarget()), sp, valueExpr);
  }

  /** Renders a decoder expression for a member target (for RawExpr union helpers). */
  static String shapeDecoderSource(Model model, MemberShape member, SymbolProvider sp) {
    return io.beam.lang.gleam.GleamRenderer.render(
        shapeDecoder(model, model.expectShape(member.getTarget()), sp));
  }

  private static Expression shapeDecoder(Model model, Shape shape, SymbolProvider sp) {
    // Enum/IntEnum before String/Number: EnumShape extends StringShape in the model API.
    if (shape instanceof EnumShape || shape instanceof IntEnumShape) {
      return LocalCallExpr.of(GleamCodecNames.decoderName(shape), List.of());
    }
    if (shape instanceof StringShape) {
      return Variable.of("decode.string");
    }
    if (shape instanceof BooleanShape) {
      return Variable.of("decode.bool");
    }
    if (shape instanceof NumberShape && !(shape instanceof BigDecimalShape)) {
      if (isFloat(shape)) {
        return Variable.of("decode.float");
      }
      return Variable.of("decode.int");
    }
    if (shape instanceof StructureShape || shape instanceof UnionShape) {
      return LocalCallExpr.of(GleamCodecNames.decoderName(shape), List.of());
    }
    if (shape instanceof ListShape list) {
      Shape element = model.expectShape(list.getMember().getTarget());
      return RemoteCallExpr.of(
          "decode", "list", List.of(shapeDecoder(model, element, sp)));
    }
    if (shape instanceof MapShape map) {
      if (GleamMapHelperDsl.mapNeedsTypedHelper(model, map)) {
        return LocalCallExpr.of(GleamCodecNames.decoderName(map), List.of());
      }
      Shape value = model.expectShape(map.getValue().getTarget());
      return RemoteCallExpr.of(
          "decode",
          "dict",
          List.of(Variable.of("decode.string"), shapeDecoder(model, value, sp)));
    }
    if (shape instanceof TimestampShape) {
      // First cut: epoch seconds as Int.
      return RawExpr.of(
          "decode.int |> decode.map(timestamp.from_unix_seconds)");
    }
    if (shape instanceof DocumentShape) {
      return RawExpr.of(
          "decode.new_primitive_decoder(\"Document\", fn(d) { Ok(d) })");
    }
    if (shape instanceof BlobShape) {
      return Variable.of("decode.bit_array");
    }
    if (shape instanceof BigDecimalShape) {
      // First cut: decode via string; failure path refined in G4.4b.
      return RawExpr.of(
          "decode.string"
              + " |> decode.then(fn(s) {"
              + " case bigdecimal.from_string(s) {"
              + " Ok(v) -> decode.success(v)"
              + " Error(_) -> decode.failure(todo as bigdecimal.BigDecimal, \"BigDecimal\")"
              + " } })");
    }
    return Variable.of("decode.string");
  }

  private static Expression encodeShape(
      Model model, Shape shape, SymbolProvider sp, Expression value) {
    if (shape instanceof EnumShape
        || shape instanceof IntEnumShape
        || shape instanceof StructureShape
        || shape instanceof UnionShape) {
      return LocalCallExpr.of(GleamCodecNames.toJsonName(shape), List.of(value));
    }
    if (shape instanceof StringShape) {
      return RemoteCallExpr.of("json", "string", List.of(value));
    }
    if (shape instanceof BooleanShape) {
      return RemoteCallExpr.of("json", "bool", List.of(value));
    }
    if (shape instanceof NumberShape && !(shape instanceof BigDecimalShape)) {
      if (isFloat(shape)) {
        return RemoteCallExpr.of("json", "float", List.of(value));
      }
      return RemoteCallExpr.of("json", "int", List.of(value));
    }
    if (shape instanceof ListShape list) {
      Shape element = model.expectShape(list.getMember().getTarget());
      return RemoteCallExpr.of(
          "json", "array", List.of(value, encodeFn(model, element, sp)));
    }
    if (shape instanceof MapShape map) {
      if (GleamMapHelperDsl.mapNeedsTypedHelper(model, map)) {
        return LocalCallExpr.of(GleamCodecNames.toJsonName(map), List.of(value));
      }
      Shape valueShape = model.expectShape(map.getValue().getTarget());
      return RawExpr.of(
          "json.dict("
              + io.beam.lang.gleam.GleamRenderer.render(value)
              + ", fn(k) { k }, "
              + io.beam.lang.gleam.GleamRenderer.render(encodeFn(model, valueShape, sp))
              + ")");
    }
    if (shape instanceof TimestampShape) {
      return RemoteCallExpr.of(
          "json",
          "int",
          List.of(
              RemoteCallExpr.of(
                  "float",
                  "truncate",
                  List.of(
                      RemoteCallExpr.of(
                          "timestamp", "to_unix_seconds", List.of(value))))));
    }
    if (shape instanceof DocumentShape) {
      // Deferred: document encode round-trip (G4.4b).
      return RemoteCallExpr.of("json", "null", List.of());
    }
    if (shape instanceof BlobShape) {
      // Deferred: base64 (G4.4b). Placeholder empty string keeps types compiling.
      return RemoteCallExpr.of("json", "string", List.of(io.beam.lang.gleam.StringExpr.of("")));
    }
    if (shape instanceof BigDecimalShape) {
      return RemoteCallExpr.of(
          "json",
          "string",
          List.of(RemoteCallExpr.of("bigdecimal", "to_string", List.of(value))));
    }
    return RemoteCallExpr.of("json", "null", List.of());
  }

  static Expression encodeFnPublic(Model model, Shape shape, SymbolProvider sp) {
    return encodeFn(model, shape, sp);
  }

  private static Expression encodeFn(Model model, Shape shape, SymbolProvider sp) {
    if (shape instanceof EnumShape
        || shape instanceof IntEnumShape
        || shape instanceof StructureShape
        || shape instanceof UnionShape
        || (shape instanceof MapShape map && GleamMapHelperDsl.mapNeedsTypedHelper(model, map))) {
      return Variable.of(GleamCodecNames.toJsonName(shape));
    }
    if (shape instanceof StringShape) {
      return Variable.of("json.string");
    }
    if (shape instanceof BooleanShape) {
      return Variable.of("json.bool");
    }
    if (shape instanceof NumberShape && !(shape instanceof BigDecimalShape)) {
      return isFloat(shape) ? Variable.of("json.float") : Variable.of("json.int");
    }
    if (shape instanceof ListShape list) {
      Shape element = model.expectShape(list.getMember().getTarget());
      return RawExpr.of(
          "fn(items) { json.array(items, "
              + io.beam.lang.gleam.GleamRenderer.render(encodeFn(model, element, sp))
              + ") }");
    }
    if (shape instanceof MapShape map) {
      Shape valueShape = model.expectShape(map.getValue().getTarget());
      return RawExpr.of(
          "fn(d) { json.dict(d, fn(k) { k }, "
              + io.beam.lang.gleam.GleamRenderer.render(encodeFn(model, valueShape, sp))
              + ") }");
    }
    if (shape instanceof TimestampShape) {
      return RawExpr.of(
          "fn(ts) { json.int(float.truncate(timestamp.to_unix_seconds(ts))) }");
    }
    if (shape instanceof DocumentShape) {
      return RawExpr.of("fn(_) { json.null() }");
    }
    if (shape instanceof BlobShape) {
      return RawExpr.of("fn(_) { json.string(\"\") }");
    }
    if (shape instanceof BigDecimalShape) {
      return RawExpr.of("fn(v) { json.string(bigdecimal.to_string(v)) }");
    }
    return Variable.of("json.string");
  }

  private static boolean isFloat(Shape shape) {
    return shape.getType() == software.amazon.smithy.model.shapes.ShapeType.FLOAT
        || shape.getType() == software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
  }

  private static Expression StringExprOf(String value) {
    return io.beam.lang.gleam.StringExpr.of(value);
  }
}
