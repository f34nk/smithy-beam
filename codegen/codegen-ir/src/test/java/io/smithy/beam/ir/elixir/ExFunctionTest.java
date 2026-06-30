package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExFunctionTest {
  @Test
  void decodeBasicItemLines() {
    ExFunction fn = buildDecodeBasicItem();
    assertThat(fn.lines())
        .containsExactly(
            "@spec decode_basic_item(nil | map()) :: nil | BasicItem.t()",
            "def decode_basic_item(:nil), do: :nil",
            "def decode_basic_item(map) when is_map(map) do",
            "  %BasicItem{",
            "    name: Map.get(map, \"name\", :nil),",
            "    count: Map.get(map, \"count\", :nil)",
            "  }",
            "end");
  }

  @Test
  void decodeBasicItemAsString() {
    ExFunction fn = buildDecodeBasicItem();
    assertThat(fn.asString())
        .isEqualTo(
            "@spec decode_basic_item(nil | map()) :: nil | BasicItem.t()\n"
                + "def decode_basic_item(:nil), do: :nil\n"
                + "def decode_basic_item(map) when is_map(map) do\n"
                + "  %BasicItem{\n"
                + "    name: Map.get(map, \"name\", :nil),\n"
                + "    count: Map.get(map, \"count\", :nil)\n"
                + "  }\n"
                + "end");
  }

  @Test
  void decodeBasicItemWithDocAsString() {
    ExFunction fn = buildDecodeBasicItemWithDoc();
    assertThat(fn.asString())
        .isEqualTo(
            "@doc \"Decode a BasicItem from a JSON map.\"\n"
                + "@spec decode_basic_item(nil | map()) :: nil | BasicItem.t()\n"
                + "def decode_basic_item(:nil), do: :nil\n"
                + "def decode_basic_item(map) when is_map(map) do\n"
                + "  %BasicItem{\n"
                + "    name: Map.get(map, \"name\", :nil),\n"
                + "    count: Map.get(map, \"count\", :nil)\n"
                + "  }\n"
                + "end");
  }

  private static ExFunction buildDecodeBasicItem() {
    return ExFunction.functionWithSpec(
        "def",
        "decode_basic_item",
        ExSpec.functionSpec("decode_basic_item", "nil | map()", "nil | BasicItem.t()"),
        decodeBasicItemClauses());
  }

  private static ExFunction buildDecodeBasicItemWithDoc() {
    return ExFunction.functionWithDocAndSpec(
        "def",
        "decode_basic_item",
        ExDoc.doc("Decode a BasicItem from a JSON map."),
        ExSpec.functionSpec("decode_basic_item", "nil | map()", "nil | BasicItem.t()"),
        decodeBasicItemClauses());
  }

  private static List<ExClause> decodeBasicItemClauses() {
    return List.of(
        ExClause.inlineClause(List.of(ExAtomPattern.atom("nil")), ExAtom.atom("nil")),
        ExClause.blockClause(
            List.of(ExVarPattern.var("map")),
            List.of(ExGuard.guard("is_map", ExVar.var("map"))),
            basicItemStruct()));
  }

  private static ExStruct basicItemStruct() {
    return ExStruct.struct(
        "BasicItem",
        ExMapEntry.entry(
            ExAtom.atom("name"),
            ExCall.call(
                "Map", "get", ExVar.var("map"), ExString.string("name"), ExAtom.atom("nil"))),
        ExMapEntry.entry(
            ExAtom.atom("count"),
            ExCall.call(
                "Map", "get", ExVar.var("map"), ExString.string("count"), ExAtom.atom("nil"))));
  }
}
