package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.Function;
import io.smithy.beam.ir.elixir.ExFunction;

public final class ElixirIrTestSupport {
  private ElixirIrTestSupport() {}

  public static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.heads()).isNotEmpty();
  }

  public static void assertStructural(ExFunction fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }
}
