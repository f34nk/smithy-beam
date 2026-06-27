package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlRecordFieldDefTest {
  @Test
  void lines() {
    assertThat(new ErlRecordFieldDef("name", "basic_string()").lines(1))
        .containsExactly("    name :: basic_string()");
  }

  @Test
  void asString() {
    assertThat(new ErlRecordFieldDef("name", "basic_string()").asString(1))
        .isEqualTo("    name :: basic_string()");
  }
}
