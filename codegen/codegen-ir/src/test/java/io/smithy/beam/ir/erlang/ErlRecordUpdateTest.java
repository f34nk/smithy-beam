package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlRecordUpdateTest {
    @Test
    void recordUpdateLines() {
        ErlRecordUpdate update = ErlRecordUpdate.recordUpdate(
                new ErlVar("Item"),
                "basic_item",
                ErlRecordField.field("name", new ErlBinary("x")));
        assertThat(update.lines()).containsExactly("Item#basic_item{ name = <<\"x\">> }");
    }

    @Test
    void recordUpdateAsString() {
        ErlRecordUpdate update = ErlRecordUpdate.recordUpdate(
                new ErlVar("Item"),
                "basic_item",
                ErlRecordField.field("name", new ErlBinary("x")));
        assertThat(update.asString()).isEqualTo("Item#basic_item{ name = <<\"x\">> }");
    }
}
