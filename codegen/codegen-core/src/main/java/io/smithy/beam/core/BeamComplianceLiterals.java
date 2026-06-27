package io.smithy.beam.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EnumValueTrait;

/** Builds BEAM literals from HTTP compliance test {@code params} nodes. */
public final class BeamComplianceLiterals {

  private BeamComplianceLiterals() {}

  public static String erlangRecordLiteral(
      Model model, StructureShape shape, ObjectNode params, SymbolProvider symbolProvider) {
    String recordName = recordName(symbolProvider.toSymbol(shape));
    if (params.isEmpty()) {
      return "#" + recordName + "{}";
    }
    List<String> fields = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String memberName = member.getMemberName();
      if (!params.getMember(memberName).isPresent()) {
        continue;
      }
      Node value = params.expectMember(memberName);
      String fieldName = BeamNameUtils.toSnakeCase(memberName);
      fields.add(fieldName + " = " + erlangValue(model, member, value, symbolProvider));
    }
    return "#" + recordName + "{" + String.join(", ", fields) + "}";
  }

  public static String elixirStructLiteral(
      Model model,
      StructureShape shape,
      ObjectNode params,
      SymbolProvider symbolProvider,
      Function<StructureShape, String> structNameFn) {
    String structName = structNameFn.apply(shape);
    if (params.isEmpty()) {
      return "%" + structName + "{}";
    }
    List<String> fields = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String memberName = member.getMemberName();
      if (!params.getMember(memberName).isPresent()) {
        continue;
      }
      Node value = params.expectMember(memberName);
      String fieldName = BeamNameUtils.toSnakeCase(memberName);
      fields.add(
          fieldName + ": " + elixirValue(model, member, value, symbolProvider, structNameFn));
    }
    return "%" + structName + "{" + String.join(", ", fields) + "}";
  }

  public static String erlangHeadersMap(Map<String, String> headers) {
    if (headers.isEmpty()) {
      return "#{}";
    }
    List<String> entries = new ArrayList<>();
    headers.forEach((key, value) -> entries.add(erlangBinary(key) + " => " + erlangBinary(value)));
    return "#{" + String.join(", ", entries) + "}";
  }

  public static String elixirHeadersMap(Map<String, String> headers) {
    if (headers.isEmpty()) {
      return "%{}";
    }
    List<String> entries = new ArrayList<>();
    headers.forEach((key, value) -> entries.add(elixirString(key) + " => " + elixirString(value)));
    return "%{" + String.join(", ", entries) + "}";
  }

  public static String erlangQueryParamsList(List<String> queryParams) {
    if (queryParams.isEmpty()) {
      return "[]";
    }
    List<String> entries = new ArrayList<>();
    for (String queryParam : queryParams) {
      entries.add(erlangBinary(queryParam));
    }
    return "[" + String.join(", ", entries) + "]";
  }

  public static String elixirQueryParamsList(List<String> queryParams) {
    if (queryParams.isEmpty()) {
      return "[]";
    }
    List<String> entries = new ArrayList<>();
    for (String queryParam : queryParams) {
      entries.add(elixirString(queryParam));
    }
    return "[" + String.join(", ", entries) + "]";
  }

  public static String erlangOptionalBinary(String value) {
    return value == null ? "undefined" : erlangBinary(value);
  }

  public static String elixirOptionalBinary(String value) {
    return value == null ? "nil" : elixirString(value);
  }

  public static String erlangMemberValue(
      Model model, MemberShape member, Node value, SymbolProvider symbolProvider) {
    return erlangValue(model, member, value, symbolProvider);
  }

  public static String elixirMemberValue(
      Model model,
      MemberShape member,
      Node value,
      SymbolProvider symbolProvider,
      Function<StructureShape, String> structNameFn) {
    return elixirValue(model, member, value, symbolProvider, structNameFn);
  }

  public static String erlangNodeValue(Node value) {
    return erlangScalar(value);
  }

  public static String elixirNodeValue(Node value) {
    return elixirScalar(value);
  }

  private static String erlangValue(
      Model model, MemberShape member, Node value, SymbolProvider symbolProvider) {
    Shape target = model.expectShape(member.getTarget());
    if (value instanceof ObjectNode objectNode && target instanceof StructureShape structureShape) {
      return erlangRecordLiteral(model, structureShape, objectNode, symbolProvider);
    }
    if (value instanceof ArrayNode arrayNode && target instanceof ListShape listShape) {
      MemberShape listMember = listShape.getMember();
      List<String> elements = new ArrayList<>();
      for (Node element : arrayNode.getElements()) {
        elements.add(erlangValue(model, listMember, element, symbolProvider));
      }
      return "[" + String.join(", ", elements) + "]";
    }
    if (value instanceof ObjectNode objectNode && target instanceof MapShape) {
      return erlangMap(objectNode);
    }
    if (target instanceof EnumShape enumShape && value instanceof StringNode stringNode) {
      return enumAtom(enumShape, stringNode.getValue(), symbolProvider);
    }
    if (target instanceof IntEnumShape intEnumShape && value instanceof NumberNode numberNode) {
      return enumAtom(intEnumShape, intEnumMemberName(intEnumShape, numberNode), symbolProvider);
    }
    return erlangScalar(value);
  }

  private static String elixirValue(
      Model model,
      MemberShape member,
      Node value,
      SymbolProvider symbolProvider,
      Function<StructureShape, String> structNameFn) {
    Shape target = model.expectShape(member.getTarget());
    if (value instanceof ObjectNode objectNode && target instanceof StructureShape structureShape) {
      return elixirStructLiteral(model, structureShape, objectNode, symbolProvider, structNameFn);
    }
    if (value instanceof ArrayNode arrayNode && target instanceof ListShape listShape) {
      MemberShape listMember = listShape.getMember();
      List<String> elements = new ArrayList<>();
      for (Node element : arrayNode.getElements()) {
        elements.add(elixirValue(model, listMember, element, symbolProvider, structNameFn));
      }
      return "[" + String.join(", ", elements) + "]";
    }
    if (value instanceof ObjectNode objectNode && target instanceof MapShape) {
      return elixirMap(objectNode);
    }
    if (target instanceof EnumShape enumShape && value instanceof StringNode stringNode) {
      return ":" + enumAtom(enumShape, stringNode.getValue(), symbolProvider);
    }
    if (target instanceof IntEnumShape intEnumShape && value instanceof NumberNode numberNode) {
      return ":"
          + enumAtom(intEnumShape, intEnumMemberName(intEnumShape, numberNode), symbolProvider);
    }
    return elixirScalar(value);
  }

  private static String enumAtom(
      Shape enumShape, String memberName, SymbolProvider symbolProvider) {
    Symbol symbol = symbolProvider.toSymbol(enumShape);
    Map<String, String> byMember =
        symbol.getProperty("enumAtomByMember", Map.class).orElse(Map.of());
    return byMember.getOrDefault(memberName, BeamNameUtils.toSnakeCase(memberName));
  }

  private static String intEnumMemberName(IntEnumShape shape, NumberNode numberNode) {
    int value = numberNode.getValue().intValue();
    for (MemberShape member : shape.members()) {
      int memberValue =
          member
              .getTrait(EnumValueTrait.class)
              .map(EnumValueTrait::expectIntValue)
              .orElse(Integer.MIN_VALUE);
      if (memberValue == value) {
        return member.getMemberName();
      }
    }
    return Integer.toString(value);
  }

  private static String erlangScalar(Node value) {
    if (value instanceof BooleanNode booleanNode) {
      return booleanNode.getValue() ? "true" : "false";
    }
    if (value instanceof NumberNode numberNode) {
      Number number = numberNode.getValue();
      if (number.doubleValue() == Math.floor(number.doubleValue())) {
        return Integer.toString(number.intValue());
      }
      return number.toString();
    }
    if (value instanceof StringNode stringNode) {
      return erlangBinary(stringNode.getValue());
    }
    if (value instanceof NullNode) {
      return "undefined";
    }
    return erlangBinary(value.toString());
  }

  private static String elixirScalar(Node value) {
    if (value instanceof BooleanNode booleanNode) {
      return booleanNode.getValue() ? "true" : "false";
    }
    if (value instanceof NumberNode numberNode) {
      Number number = numberNode.getValue();
      if (number.doubleValue() == Math.floor(number.doubleValue())) {
        return Integer.toString(number.intValue());
      }
      return number.toString();
    }
    if (value instanceof StringNode stringNode) {
      return elixirString(stringNode.getValue());
    }
    if (value instanceof NullNode) {
      return "nil";
    }
    return elixirString(value.toString());
  }

  private static String erlangMap(ObjectNode objectNode) {
    if (objectNode.getMembers().isEmpty()) {
      return "#{}";
    }
    List<String> entries = new ArrayList<>();
    objectNode
        .getMembers()
        .forEach(
            (key, node) -> {
              entries.add(erlangBinary(key.getValue()) + " => " + erlangScalar(node));
            });
    return "#{" + String.join(", ", entries) + "}";
  }

  private static String elixirMap(ObjectNode objectNode) {
    if (objectNode.getMembers().isEmpty()) {
      return "%{}";
    }
    List<String> entries = new ArrayList<>();
    objectNode
        .getMembers()
        .forEach(
            (key, node) -> {
              entries.add(elixirString(key.getValue()) + " => " + elixirScalar(node));
            });
    return "%{" + String.join(", ", entries) + "}";
  }

  private static String erlangBinary(String value) {
    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
    return "<<" + '"' + escaped + '"' + ">>";
  }

  private static String elixirString(String value) {
    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
    return "\"" + escaped + "\"";
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }
}
