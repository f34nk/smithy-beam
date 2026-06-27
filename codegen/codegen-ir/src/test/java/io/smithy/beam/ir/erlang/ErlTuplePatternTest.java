package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlTuplePatternTest {
  @Test
  void tuplePatternLines() {
    ErlTuplePattern pattern =
        ErlTuplePattern.tuplePattern(new ErlVarPattern("A"), new ErlVarPattern("B"));
    assertThat(pattern.lines()).containsExactly("{A, B}");
  }

  @Test
  void tuplePatternAsString() {
    ErlTuplePattern pattern =
        ErlTuplePattern.tuplePattern(new ErlVarPattern("A"), new ErlVarPattern("B"));
    assertThat(pattern.asString()).isEqualTo("{A, B}");
  }
}
