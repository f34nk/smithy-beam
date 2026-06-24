package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlFunctionDocTest {
    @Test
    void singleLineAsString() {
        assertThat(ErlFunctionDoc.functionDoc("Decode request body.").asString())
                .isEqualTo("-doc \"Decode request body.\".");
    }

    @Test
    void multilineAsString() {
        assertThat(ErlFunctionDoc.functionDoc("Line one.\n\nLine two.").asString())
                .isEqualTo("-doc \"\"\"\nLine one.\n\nLine two.\n\"\"\".");
    }
}
