package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.Function;

public final class ElixirIrTestSupport {
  private ElixirIrTestSupport() {}

  public static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.heads()).isNotEmpty();
  }
}
