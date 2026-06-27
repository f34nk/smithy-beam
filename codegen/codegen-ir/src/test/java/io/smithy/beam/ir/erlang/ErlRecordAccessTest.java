package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlRecordAccessTest {
  @Test
  void recordAccessLines() {
    ErlRecordAccess access = ErlRecordAccess.recordAccess(new ErlVar("Item"), "basic_item", "name");
    assertThat(access.lines()).containsExactly("Item#basic_item.name");
  }

  @Test
  void recordAccessAsString() {
    ErlRecordAccess access = ErlRecordAccess.recordAccess(new ErlVar("Item"), "basic_item", "name");
    assertThat(access.asString()).isEqualTo("Item#basic_item.name");
  }
}
