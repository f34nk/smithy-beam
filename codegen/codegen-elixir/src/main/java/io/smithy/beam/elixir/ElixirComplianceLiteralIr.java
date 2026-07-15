package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.BooleanExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.NilExpr;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StructExpr;
import io.beam.dsl.elixir.StructField;
import io.beam.dsl.elixir.Variable;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamNameUtils;
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
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EnumValueTrait;

final class ElixirComplianceLiteralIr {
  private ElixirComplianceLiteralIr() {}

  static Expression structLiteral(
      Model model,
      StructureShape shape,
      ObjectNode params,
      SymbolProvider sp,
      Function<StructureShape, String> structNameFn) {
    String structMod = structNameFn.apply(shape);
    if (params.isEmpty()) {
      return StructExpr.of(structMod, List.of());
    }
    List<StructField> fields = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String memberName = member.getMemberName();
      if (!params.getMember(memberName).isPresent()) {
        continue;
      }
      fields.add(
          StructField.of(
              BeamNameUtils.toSnakeCase(memberName),
              memberValue(model, member, params.expectMember(memberName), sp, structNameFn)));
    }
    return StructExpr.of(structMod, fields);
  }

  static Expression headersMap(Map<String, String> headers) {
    if (headers.isEmpty()) {
      return MapExpr.of(List.of());
    }
    List<MapEntry> entries = new ArrayList<>();
    headers.forEach((key, value) -> entries.add(MapEntry.stringKey(key, StringExpr.of(value))));
    return MapExpr.of(entries);
  }

  static Expression queryParamsList(List<String> queryParams) {
    if (queryParams.isEmpty()) {
      return ListExpr.of(List.of());
    }
    List<Expression> entries = new ArrayList<>();
    for (String queryParam : queryParams) {
      entries.add(StringExpr.of(queryParam));
    }
    return ListExpr.of(entries);
  }

  static Expression optionalBinary(String value) {
    return value == null ? NilExpr.of() : StringExpr.of(value);
  }

  static Expression labelMap(
      BeamHostLabelIndex hostLabelIndex, OperationShape operation, ObjectNode params) {
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : hostLabelIndex.hostLabelMembers(operation)) {
      String memberName = member.getMemberName();
      if (params.getMember(memberName).isPresent()) {
        String field = BeamNameUtils.toSnakeCase(memberName);
        entries.add(MapEntry.atomKey(field, scalarValue(params.expectMember(memberName))));
      }
    }
    if (entries.isEmpty()) {
      return MapExpr.of(List.of());
    }
    return MapExpr.of(entries);
  }

  static Expression memberValue(
      Model model,
      MemberShape member,
      Node value,
      SymbolProvider sp,
      Function<StructureShape, String> structNameFn) {
    Shape target = model.expectShape(member.getTarget());
    if (value instanceof ObjectNode objectNode && target instanceof StructureShape structureShape) {
      return structLiteral(model, structureShape, objectNode, sp, structNameFn);
    }
    if (value instanceof ArrayNode arrayNode && target instanceof ListShape listShape) {
      MemberShape listMember = listShape.getMember();
      List<Expression> elements = new ArrayList<>();
      for (Node element : arrayNode.getElements()) {
        elements.add(memberValue(model, listMember, element, sp, structNameFn));
      }
      return ListExpr.of(elements);
    }
    if (value instanceof ObjectNode objectNode && target instanceof MapShape) {
      return elixirMap(objectNode);
    }
    if (target instanceof EnumShape enumShape && value instanceof StringNode stringNode) {
      return AtomExpr.of(enumAtom(enumShape, stringNode.getValue(), sp));
    }
    if (target instanceof IntEnumShape intEnumShape && value instanceof NumberNode numberNode) {
      return AtomExpr.of(enumAtom(intEnumShape, intEnumMemberName(intEnumShape, numberNode), sp));
    }
    return scalarValue(value);
  }

  private static Expression elixirMap(ObjectNode objectNode) {
    if (objectNode.getMembers().isEmpty()) {
      return MapExpr.of(List.of());
    }
    List<MapEntry> entries = new ArrayList<>();
    objectNode
        .getMembers()
        .forEach((key, node) -> entries.add(MapEntry.stringKey(key.getValue(), scalarValue(node))));
    return MapExpr.of(entries);
  }

  private static Expression scalarValue(Node value) {
    if (value instanceof BooleanNode booleanNode) {
      return BooleanExpr.of(booleanNode.getValue());
    }
    if (value instanceof NumberNode numberNode) {
      Number number = numberNode.getValue();
      if (number.doubleValue() == Math.floor(number.doubleValue())) {
        return IntegerExpr.of(number.longValue());
      }
      return Variable.of(number.toString());
    }
    if (value instanceof StringNode stringNode) {
      return StringExpr.of(stringNode.getValue());
    }
    if (value instanceof NullNode) {
      return NilExpr.of();
    }
    return StringExpr.of(value.toString());
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
}
