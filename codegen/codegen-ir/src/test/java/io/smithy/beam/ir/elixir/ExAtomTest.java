package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExAtomTest {
  @Test
  void lines() {
    assertThat(ExAtom.atom("ok").lines()).containsExactly(":ok");
  }

  @Test
  void asString() {
    assertThat(ExAtom.atom("ok").asString()).isEqualTo(":ok");
  }

  @Test
  void linesAlreadyPrefixed() {
    assertThat(ExAtom.atom(":error").lines()).containsExactly(":error");
  }

  @Test
  void asStringAlreadyPrefixed() {
    assertThat(ExAtom.atom(":error").asString()).isEqualTo(":error");
  }
}
