package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExUseAttrTest {
  @Test
  void linesWithOptions() {
    assertThat(ExUseAttr.use("ExUnit.Case", "async: true").lines(1))
        .containsExactly("  use ExUnit.Case, async: true");
  }
}
