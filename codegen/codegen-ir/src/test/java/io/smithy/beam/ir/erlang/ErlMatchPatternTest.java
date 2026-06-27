package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlMatchPatternTest {
  @Test
  void matchPatternLines() {
    ErlMatchPattern pattern =
        ErlMatchPattern.matchPattern(
            ErlMapPattern.mapPattern(
                ErlMapFieldPattern.fieldPattern("scheme", ErlVarPattern.varPattern("Scheme")),
                ErlMapFieldPattern.fieldPattern("host", ErlVarPattern.varPattern("Host"))),
            ErlVarPattern.varPattern("Parts"));
    assertThat(pattern.lines()).containsExactly("#{scheme := Scheme, host := Host} = Parts");
  }

  @Test
  void matchPatternAsString() {
    ErlMatchPattern pattern =
        ErlMatchPattern.matchPattern(
            ErlMapPattern.mapPattern(
                ErlMapFieldPattern.fieldPattern("scheme", ErlVarPattern.varPattern("Scheme")),
                ErlMapFieldPattern.fieldPattern("host", ErlVarPattern.varPattern("Host"))),
            ErlVarPattern.varPattern("Parts"));
    assertThat(pattern.asString()).isEqualTo("#{scheme := Scheme, host := Host} = Parts");
  }
}
