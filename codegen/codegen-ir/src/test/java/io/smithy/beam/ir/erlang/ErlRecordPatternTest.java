package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlRecordPatternTest {
  @Test
  void recordPatternLines() {
    ErlRecordPattern pattern =
        ErlRecordPattern.recordPattern(
            "basic_item", ErlRecordFieldPattern.fieldPattern("name", new ErlVarPattern("Name")));
    assertThat(pattern.lines()).containsExactly("#basic_item{name = Name}");
  }

  @Test
  void recordPatternAsString() {
    ErlRecordPattern pattern =
        ErlRecordPattern.recordPattern(
            "basic_item", ErlRecordFieldPattern.fieldPattern("name", new ErlVarPattern("Name")));
    assertThat(pattern.asString()).isEqualTo("#basic_item{name = Name}");
  }

  @Test
  void recordFunctionHeadAsString() {
    ErlRecordPattern pattern =
        ErlRecordPattern.recordFunctionHead(
            "Input", "input_record", java.util.List.of("field_a", "field_b"));
    assertThat(pattern.asString()).isEqualTo("Input = #input_record{field_a, field_b}");
  }
}
