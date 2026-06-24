package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlRecordPatternTest {
    @Test
    void recordPatternLines() {
        ErlRecordPattern pattern = ErlRecordPattern.recordPattern(
                "basic_item",
                ErlRecordFieldPattern.fieldPattern("name", new ErlVarPattern("Name")));
        assertThat(pattern.lines()).containsExactly("#basic_item{name = Name}");
    }

    @Test
    void recordPatternAsString() {
        ErlRecordPattern pattern = ErlRecordPattern.recordPattern(
                "basic_item",
                ErlRecordFieldPattern.fieldPattern("name", new ErlVarPattern("Name")));
        assertThat(pattern.asString()).isEqualTo("#basic_item{name = Name}");
    }
}
