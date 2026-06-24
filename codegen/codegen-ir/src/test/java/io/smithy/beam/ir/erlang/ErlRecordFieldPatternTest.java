package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlRecordFieldPatternTest {
    @Test
    void fieldShorthandAsString() {
        assertThat(ErlRecordFieldPattern.field("field_a").asString()).isEqualTo("field_a");
    }
}
