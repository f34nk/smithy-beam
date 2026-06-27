package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlRecordUpdateTest {
  @Test
  void recordUpdateLines() {
    ErlRecordUpdate update =
        ErlRecordUpdate.recordUpdate(
            new ErlVar("Item"), "basic_item", ErlRecordField.field("name", new ErlBinary("x")));
    assertThat(update.lines()).containsExactly("Item#basic_item{ name = <<\"x\">> }");
  }

  @Test
  void recordUpdateAsString() {
    ErlRecordUpdate update =
        ErlRecordUpdate.recordUpdate(
            new ErlVar("Item"), "basic_item", ErlRecordField.field("name", new ErlBinary("x")));
    assertThat(update.asString()).isEqualTo("Item#basic_item{ name = <<\"x\">> }");
  }
}
