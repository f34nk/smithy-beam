package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExNil;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStruct;
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

  static ExExpr structLiteral(
      Model model,
      StructureShape shape,
      ObjectNode params,
      SymbolProvider sp,
      Function<StructureShape, String> structNameFn) {
    String structMod = structNameFn.apply(shape);
    if (params.isEmpty()) {
      return ExStruct.struct(structMod, List.of());
    }
    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String memberName = member.getMemberName();
      if (!params.getMember(memberName).isPresent()) {
        continue;
      }
      fields.add(
          ExMapEntry.entry(
              ExAtom.atom(BeamNameUtils.toSnakeCase(memberName)),
              memberValue(model, member, params.expectMember(memberName), sp, structNameFn)));
    }
    return ExStruct.struct(structMod, fields);
  }

  static ExExpr headersMap(Map<String, String> headers) {
    if (headers.isEmpty()) {
      return ExMap.map();
    }
    List<ExMapEntry> entries = new ArrayList<>();
    headers.forEach(
        (key, value) ->
            entries.add(ExMapEntry.entry(ExString.string(key), ExString.string(value))));
    return ExMap.map(entries.toArray(ExMapEntry[]::new));
  }

  static ExExpr queryParamsList(List<String> queryParams) {
    if (queryParams.isEmpty()) {
      return ExList.list();
    }
    List<ExExpr> entries = new ArrayList<>();
    for (String queryParam : queryParams) {
      entries.add(ExString.string(queryParam));
    }
    return ExList.list(entries.toArray(ExExpr[]::new));
  }

  static ExExpr optionalBinary(String value) {
    return value == null ? ExNil.nil() : ExString.string(value);
  }

  static ExExpr labelMap(
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      ObjectNode params) {
    List<ExMapEntry> entries = new ArrayList<>();
    for (MemberShape member : hostLabelIndex.hostLabelMembers(operation)) {
      String memberName = member.getMemberName();
      if (params.getMember(memberName).isPresent()) {
        String field = BeamNameUtils.toSnakeCase(memberName);
        entries.add(
            ExMapEntry.entry(
                ExAtom.atom(field), scalarValue(params.expectMember(memberName))));
      }
    }
    if (entries.isEmpty()) {
      return ExMap.map();
    }
    return ExMap.map(entries.toArray(ExMapEntry[]::new));
  }

  static ExExpr memberValue(
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
      List<ExExpr> elements = new ArrayList<>();
      for (Node element : arrayNode.getElements()) {
        elements.add(memberValue(model, listMember, element, sp, structNameFn));
      }
      return ExList.list(elements.toArray(ExExpr[]::new));
    }
    if (value instanceof ObjectNode objectNode && target instanceof MapShape) {
      return elixirMap(objectNode);
    }
    if (target instanceof EnumShape enumShape && value instanceof StringNode stringNode) {
      return ExAtom.atom(enumAtom(enumShape, stringNode.getValue(), sp));
    }
    if (target instanceof IntEnumShape intEnumShape && value instanceof NumberNode numberNode) {
      return ExAtom.atom(enumAtom(intEnumShape, intEnumMemberName(intEnumShape, numberNode), sp));
    }
    return scalarValue(value);
  }

  private static ExExpr elixirMap(ObjectNode objectNode) {
    if (objectNode.getMembers().isEmpty()) {
      return ExMap.map();
    }
    List<ExMapEntry> entries = new ArrayList<>();
    objectNode
        .getMembers()
        .forEach(
            (key, node) ->
                entries.add(
                    ExMapEntry.entry(ExString.string(key.getValue()), scalarValue(node))));
    return ExMap.map(entries.toArray(ExMapEntry[]::new));
  }

  private static ExExpr scalarValue(Node value) {
    if (value instanceof BooleanNode booleanNode) {
      return ExCapturedBlock.capturedBlock(booleanNode.getValue() ? "true" : "false");
    }
    if (value instanceof NumberNode numberNode) {
      Number number = numberNode.getValue();
      if (number.doubleValue() == Math.floor(number.doubleValue())) {
        return ExInteger.integer(number.longValue());
      }
      return ExCapturedBlock.capturedBlock(number.toString());
    }
    if (value instanceof StringNode stringNode) {
      return ExString.string(stringNode.getValue());
    }
    if (value instanceof NullNode) {
      return ExNil.nil();
    }
    return ExString.string(value.toString());
  }

  private static String enumAtom(Shape enumShape, String memberName, SymbolProvider symbolProvider) {
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
