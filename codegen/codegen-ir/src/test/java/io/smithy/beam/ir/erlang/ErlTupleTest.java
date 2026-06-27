package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlTupleTest {
  @Test
  void tupleLines() {
    ErlTuple tuple = ErlTuple.tuple(ErlAtom.atom("ok"), new ErlVar("Value"));
    assertThat(tuple.lines()).containsExactly("{ok, Value}");
  }

  @Test
  void tupleAsString() {
    ErlTuple tuple = ErlTuple.tuple(ErlAtom.atom("ok"), new ErlVar("Value"));
    assertThat(tuple.asString()).isEqualTo("{ok, Value}");
  }
}
