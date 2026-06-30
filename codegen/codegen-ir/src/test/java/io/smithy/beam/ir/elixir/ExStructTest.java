package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExStructTest {
  @Test
  void singleFieldStructLines() {
    ExStruct struct =
        ExStruct.struct("BasicItem", ExMapEntry.entry(ExAtom.atom("name"), ExVar.var("name")));
    assertThat(struct.lines()).containsExactly("%BasicItem{name: name}");
  }

  @Test
  void singleFieldStructAsString() {
    ExStruct struct =
        ExStruct.struct("BasicItem", ExMapEntry.entry(ExAtom.atom("name"), ExVar.var("name")));
    assertThat(struct.asString()).isEqualTo("%BasicItem{name: name}");
  }

  @Test
  void multilineStructAsString() {
    ExStruct struct =
        ExStruct.struct(
            "BasicItem",
            ExMapEntry.entry(
                ExAtom.atom("name"),
                ExCall.call(
                    "Map", "get", ExVar.var("map"), ExString.string("name"), ExAtom.atom("nil"))),
            ExMapEntry.entry(
                ExAtom.atom("count"),
                ExCall.call(
                    "Map", "get", ExVar.var("map"), ExString.string("count"), ExAtom.atom("nil"))));
    assertThat(struct.asString(1))
        .isEqualTo(
            "  %BasicItem{\n"
                + "    name: Map.get(map, \"name\", :nil),\n"
                + "    count: Map.get(map, \"count\", :nil)\n"
                + "  }");
  }
}
