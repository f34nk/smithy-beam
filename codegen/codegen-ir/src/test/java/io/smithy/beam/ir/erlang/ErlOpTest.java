package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlOpTest {
  @Test
  void opLines() {
    ErlOp op = ErlOp.op("+", new ErlVar("A"), new ErlVar("B"));
    assertThat(op.lines()).containsExactly("A + B");
  }

  @Test
  void opAsString() {
    ErlOp op = ErlOp.op("+", new ErlVar("A"), new ErlVar("B"));
    assertThat(op.asString()).isEqualTo("A + B");
  }
}
