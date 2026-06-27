package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExLayoutTest {
  @Test
  void renderAtomPlain() {
    assertThat(ExLayout.renderAtom("ok")).isEqualTo(":ok");
  }

  @Test
  void renderAtomAlreadyPrefixed() {
    assertThat(ExLayout.renderAtom(":ok")).isEqualTo(":ok");
  }

  @Test
  void renderAtomNumericPrefix() {
    assertThat(ExLayout.renderAtom("404")).isEqualTo("404");
  }

  @Test
  void renderAtomWithDash() {
    assertThat(ExLayout.renderAtom("foo-bar")).isEqualTo(":\"oo-bar\"");
  }

  @Test
  void renderStringEscapesQuotes() {
    assertThat(ExLayout.renderString("say \"hi\"")).isEqualTo("\"say \\\"hi\\\"\"");
  }

  @Test
  void renderHashCommentSingleLine() {
    assertThat(ExLayout.renderHashComment("Generated codec.", 0))
        .containsExactly("# Generated codec.");
  }

  @Test
  void renderHashCommentMultiline() {
    assertThat(ExLayout.renderHashComment("Line one.\n\nLine two.", 1))
        .containsExactly("  # Line one.", "  #", "  # Line two.");
  }

  @Test
  void renderHashCommentEmpty() {
    assertThat(ExLayout.renderHashComment("", 0)).containsExactly("#");
  }

  @Test
  void renderDocAttributeSingleLine() {
    assertThat(ExLayout.renderDocAttribute("@doc", "REST JSON codec.", 1))
        .containsExactly("  @doc \"REST JSON codec.\"");
  }

  @Test
  void renderDocAttributeMultiline() {
    assertThat(ExLayout.renderDocAttribute("@moduledoc", "Line one.\n\nLine two.", 1))
        .containsExactly(
            "  @moduledoc \"\"\"",
            "    Line one.",
            "",
            "    Line two.",
            "  \"\"\"");
  }
}
