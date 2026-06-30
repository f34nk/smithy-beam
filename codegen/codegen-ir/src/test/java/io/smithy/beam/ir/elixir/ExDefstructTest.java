package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExDefstructTest {
  @Test
  void smallDefstructOnOneLine() {
    assertThat(ExDefstruct.defstruct(List.of(":name", ":count")).asString())
        .isEqualTo("defstruct [:name, :count]");
  }

  @Test
  void largeDefstructMultiline() {
    String out = ExDefstruct.defstruct(List.of(":a", ":b", ":c", ":d", ":e")).asString();
    assertThat(out).contains("defstruct [");
    assertThat(out).contains("  :a,");
    assertThat(out).contains("  :e");
    assertThat(out).contains("]");
  }
}
