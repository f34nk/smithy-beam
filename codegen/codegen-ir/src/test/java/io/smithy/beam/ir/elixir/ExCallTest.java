package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExCallTest {
  @Test
  void remoteCallLines() {
    ExCall call =
        ExCall.call(
            "Map",
            "get",
            ExString.string("name"),
            ExVar.var("map"),
            ExAtom.atom("nil"));
    assertThat(call.lines()).containsExactly("Map.get(\"name\", map, :nil)");
  }

  @Test
  void remoteCallAsString() {
    ExCall call =
        ExCall.call(
            "Map",
            "get",
            ExString.string("name"),
            ExVar.var("map"),
            ExAtom.atom("nil"));
    assertThat(call.asString()).isEqualTo("Map.get(\"name\", map, :nil)");
  }
}
