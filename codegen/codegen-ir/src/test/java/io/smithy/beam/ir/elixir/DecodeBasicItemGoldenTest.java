package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecodeBasicItemGoldenTest {
  @Test
  void buildsDecodeBasicItemFunction() {
    ExFunction decodeBasicItem = decodeBasicItemFunction(null);

    assertThat(decodeBasicItem.name()).isEqualTo("decode_basic_item");
    assertThat(decodeBasicItem.clauses()).hasSize(2);

    ExClause mapClause = decodeBasicItem.clauses().get(1);
    assertThat(mapClause.guards()).hasSize(1);
    assertThat(mapClause.body()).hasSize(1);
    assertThat(mapClause.body().get(0)).isInstanceOf(ExStruct.class);

    ExStruct struct = (ExStruct) mapClause.body().get(0);
    assertThat(struct.moduleName()).isEqualTo("BasicItem");
    assertThat(struct.fields()).hasSize(2);
    assertThat(struct.fields().get(0).value()).isInstanceOf(ExCall.class);
    assertThat(struct.fields().get(1).value()).isInstanceOf(ExCall.class);
  }

  @Test
  void decodeBasicItemLinesMatchGolden() throws IOException {
    ExFunction decodeBasicItem =
        decodeBasicItemFunction(
            ExSpec.functionSpec("decode_basic_item", "nil | map()", "nil | BasicItem.t()"));

    assertThat(decodeBasicItem.lines())
        .isEqualTo(readExpectedLines("ir/decode_basic_item.expected.ex"));
  }

  @Test
  void decodeBasicItemAsStringMatchGolden() throws IOException {
    ExFunction decodeBasicItem =
        decodeBasicItemFunction(
            ExSpec.functionSpec("decode_basic_item", "nil | map()", "nil | BasicItem.t()"));

    assertThat(decodeBasicItem.asString())
        .isEqualTo(readExpectedString("ir/decode_basic_item.expected.ex"));
  }

  private static ExFunction decodeBasicItemFunction(ExSpec specOrNull) {
    List<ExClause> clauses =
        List.of(
            ExClause.inlineClause(List.of(ExAtomPattern.atom("nil")), ExAtom.atom("nil")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("map")),
                List.of(ExGuard.guard("is_map", ExVar.var("map"))),
                basicItemStruct()));

    if (specOrNull == null) {
      return ExFunction.defFunction("decode_basic_item", clauses);
    }
    return ExFunction.functionWithSpec("def", "decode_basic_item", specOrNull, clauses);
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

  private static List<String> readExpectedLines(String resourcePath) throws IOException {
    try (InputStream in =
        DecodeBasicItemGoldenTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return Arrays.asList(text.split("\n", -1));
    }
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        DecodeBasicItemGoldenTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
