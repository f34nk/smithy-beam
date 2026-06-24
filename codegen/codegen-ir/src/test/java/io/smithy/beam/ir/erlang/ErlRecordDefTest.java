package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlRecordDefTest {
    @Test
    void lines() {
        ErlRecordDef record = basicItemRecord();
        assertThat(record.lines()).containsExactly(
                "-record(basic_item, {",
                "    name  :: basic_string(),",
                "    count  :: basic_integer() | undefined",
                "}).");
    }

    @Test
    void asString() {
        ErlRecordDef record = basicItemRecord();
        assertThat(record.asString()).isEqualTo(
                "-record(basic_item, {\n"
                        + "    name  :: basic_string(),\n"
                        + "    count  :: basic_integer() | undefined\n"
                        + "}).");
    }

    @Test
    void emptyRecordLines() {
        assertThat(new ErlRecordDef("health_check_input", List.of()).lines())
                .containsExactly("-record(health_check_input, {}).");
    }

    private static ErlRecordDef basicItemRecord() {
        return new ErlRecordDef(
                "basic_item",
                List.of(
                        new ErlRecordFieldDef("name", "basic_string()"),
                        new ErlRecordFieldDef("count", "basic_integer() | undefined")));
    }
}
