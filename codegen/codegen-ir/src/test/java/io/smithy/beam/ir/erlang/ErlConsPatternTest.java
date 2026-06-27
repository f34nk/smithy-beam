package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlConsPatternTest {
  @Test
  void consPatternLines() {
    ErlConsPattern pattern =
        ErlConsPattern.consPattern(new ErlVarPattern("H"), new ErlVarPattern("T"));
    assertThat(pattern.lines()).containsExactly("[H | T]");
  }

  @Test
  void consPatternAsString() {
    ErlConsPattern pattern =
        ErlConsPattern.consPattern(new ErlVarPattern("H"), new ErlVarPattern("T"));
    assertThat(pattern.asString()).isEqualTo("[H | T]");
  }
}
