package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamNameUtils;
import java.util.Map;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.JsonNameTrait;

final class GleamCodecNames {

  private GleamCodecNames() {}

  static String helperStem(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  static String toJsonName(Shape shape) {
    return helperStem(shape) + "_to_json";
  }

  static String jsonToName(Shape shape) {
    return "json_to_" + helperStem(shape);
  }

  static String decoderName(Shape shape) {
    return helperStem(shape) + "_decoder";
  }

  static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }

  static String constructorForMember(SymbolProvider sp, Shape container, String memberName) {
    Symbol symbol = sp.toSymbol(container);
    @SuppressWarnings("unchecked")
    Map<String, String> byMember =
        (Map<String, String>)
            symbol
                .getProperty("enumConstructorByMember", Map.class)
                .or(() -> symbol.getProperty("unionConstructorByMember", Map.class))
                .orElseThrow(
                    () ->
                        new CodegenException(
                            "Missing constructor map on symbol for " + container.getId()));
    String name = byMember.get(memberName);
    if (name == null) {
      throw new CodegenException(
          "Missing constructor for member " + memberName + " on " + container.getId());
    }
    return name;
  }

  static String unknownConstructor(SymbolProvider sp, Shape container) {
    return sp.toSymbol(container)
        .getProperty("unknownConstructor", String.class)
        .orElseThrow(
            () ->
                new CodegenException(
                    "Missing unknownConstructor on symbol for " + container.getId()));
  }

  static String structureConstructor(SymbolProvider sp, Shape structure) {
    Symbol sym = sp.toSymbol(structure);
    return sym.getProperty("constructorName", String.class).orElse(sym.getName());
  }

  static String qualifiedType(String typesModule, SymbolProvider sp, Shape shape) {
    return GleamTopDown.qualifiedType(typesModule, sp.toSymbol(shape));
  }

  /** Qualify a custom-type constructor for use outside the types module. */
  static String qualifiedConstructor(String typesModule, String constructor) {
    return typesModule + "." + constructor;
  }
}
