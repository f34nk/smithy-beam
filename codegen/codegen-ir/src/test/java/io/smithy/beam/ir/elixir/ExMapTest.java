package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExMapTest {
  @Test
  void mapLiteralLines() {
    ExMap map = ExMap.map(ExMapEntry.entry(ExString.string("k"), ExVar.var("v")));
    assertThat(map.lines()).containsExactly("%{\"k\" => v}");
  }

  @Test
  void mapLiteralAsString() {
    ExMap map = ExMap.map(ExMapEntry.entry(ExString.string("k"), ExVar.var("v")));
    assertThat(map.asString()).isEqualTo("%{\"k\" => v}");
  }

  @Test
  void multilineMapAsString() {
    ExMap map =
        ExMap.map(
            ExMapEntry.entry(ExString.string("a"), ExVar.var("a")),
            ExMapEntry.entry(ExString.string("b"), ExVar.var("b")));
    assertThat(map.asString())
        .isEqualTo("%{\n  \"a\" => a,\n  \"b\" => b\n}");
  }
}
