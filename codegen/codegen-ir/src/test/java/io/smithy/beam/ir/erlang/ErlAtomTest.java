package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlAtomTest {
  @Test
  void lines() {
    assertThat(new ErlAtom("undefined").lines()).containsExactly("undefined");
  }

  @Test
  void asString() {
    assertThat(new ErlAtom("undefined").asString()).isEqualTo("undefined");
  }

  @Test
  void linesXmlElementAtomUnquoted() {
    assertThat(new ErlAtom("xmlElement").asString()).isEqualTo("xmlElement");
  }

  @Test
  void linesQuoted() {
    assertThat(new ErlAtom("Region").lines()).containsExactly("'Region'");
  }

  @Test
  void asStringQuoted() {
    assertThat(new ErlAtom("Region").asString()).isEqualTo("'Region'");
  }
}
