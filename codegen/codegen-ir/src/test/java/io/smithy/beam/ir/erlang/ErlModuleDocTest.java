package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlModuleDocTest {
    @Test
    void singleLineAsString() {
        assertThat(ErlModuleDoc.moduleDoc("REST JSON codec.").asString())
                .isEqualTo("-moduledoc \"REST JSON codec.\".");
    }

    @Test
    void multilineAsString() {
        assertThat(ErlModuleDoc.moduleDoc("Line one.\n\nLine two.").asString())
                .isEqualTo("-moduledoc \"\"\"\nLine one.\n\nLine two.\n\"\"\".");
    }
}
