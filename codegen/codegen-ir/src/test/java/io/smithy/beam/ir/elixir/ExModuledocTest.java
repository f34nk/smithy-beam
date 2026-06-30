package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExModuledocTest {
  @Test
  void singleLineAsString() {
    assertThat(ExModuledoc.moduledoc("REST JSON codec.").asString())
        .isEqualTo("@moduledoc \"REST JSON codec.\"");
  }

  @Test
  void multilineAsString() {
    assertThat(ExModuledoc.moduledoc("Line one.\n\nLine two.").asString())
        .isEqualTo("@moduledoc \"\"\"\n  Line one.\n\n  Line two.\n\"\"\"");
  }
}
