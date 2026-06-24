package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlLayoutTest {
    @Test
    void rendersBareAtom() {
        assertThat(ErlLayout.renderAtom("undefined")).isEqualTo("undefined");
    }

    @Test
    void rendersQuotedAtom() {
        assertThat(ErlLayout.renderAtom("Region")).isEqualTo("'Region'");
    }

    @Test
    void rendersBinaryLiteral() {
        assertThat(ErlLayout.renderBinaryLiteral("name")).isEqualTo("<<\"name\">>");
    }

    @Test
    void renderPercentCommentSingleLine() {
        assertThat(ErlLayout.renderPercentComment("DO NOT EDIT", 0))
                .containsExactly("%% DO NOT EDIT");
    }

    @Test
    void renderPercentCommentMultiline() {
        assertThat(ErlLayout.renderPercentComment("Line one.\n\nLine two.", 0))
                .containsExactly("%% Line one.", "%%", "%% Line two.");
    }

    @Test
    void renderDocAttributeSingleLine() {
        assertThat(ErlLayout.renderDocAttribute("moduledoc", "Brief.", 0))
                .containsExactly("-moduledoc \"Brief.\".");
    }

    @Test
    void renderDocAttributeMultiline() {
        assertThat(ErlLayout.renderDocAttribute("doc", "Line one.\n\nLine two.", 0))
                .containsExactly(
                        "-doc \"\"\"",
                        "Line one.",
                        "",
                        "Line two.",
                        "\"\"\".");
    }
}
