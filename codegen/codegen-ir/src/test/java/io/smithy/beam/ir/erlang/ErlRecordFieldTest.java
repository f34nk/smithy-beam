package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlRecordFieldTest {
    @Test
    void lines() {
        ErlRecordField field = new ErlRecordField("name", new ErlBinary("updated"));
        assertThat(field.lines(0)).containsExactly("name = <<\"updated\">>");
    }

    @Test
    void asString() {
        ErlRecordField field = new ErlRecordField("name", new ErlBinary("updated"));
        assertThat(field.asString()).isEqualTo("name = <<\"updated\">>");
    }
}
