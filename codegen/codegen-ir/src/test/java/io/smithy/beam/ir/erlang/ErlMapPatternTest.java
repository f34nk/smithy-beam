package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlMapPatternTest {
  @Test
  void mapPatternLines() {
    ErlMapPattern pattern =
        ErlMapPattern.mapPattern(
            ErlMapFieldPattern.fieldPattern("scheme", ErlVarPattern.varPattern("Scheme")),
            ErlMapFieldPattern.fieldPattern("host", ErlVarPattern.varPattern("Host")));
    assertThat(pattern.lines()).containsExactly("#{scheme := Scheme, host := Host}");
  }

  @Test
  void mapPatternAsString() {
    ErlMapPattern pattern =
        ErlMapPattern.mapPattern(
            ErlMapFieldPattern.fieldPattern("scheme", ErlVarPattern.varPattern("Scheme")),
            ErlMapFieldPattern.fieldPattern("host", ErlVarPattern.varPattern("Host")));
    assertThat(pattern.asString()).isEqualTo("#{scheme := Scheme, host := Host}");
  }
}
