package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlFunctionDocTest {
  @Test
  void singleLineAsString() {
    assertThat(ErlFunctionDoc.functionDoc("Decode request body.").asString())
        .isEqualTo("%% @doc Decode request body.");
  }

  @Test
  void multilineAsString() {
    assertThat(ErlFunctionDoc.functionDoc("Line one.\n\nLine two.").asString())
        .isEqualTo("%% @doc\n%% Line one.\n%%\n%% Line two.");
  }
}
