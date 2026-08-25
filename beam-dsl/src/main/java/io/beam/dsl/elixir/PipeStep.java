package io.beam.dsl.elixir;

import java.util.List;

public record PipeStep(Expression callable, List<Expression> extraArgs) {

  public static PipeStep of(Expression callable, List<Expression> extraArgs) {
    return new PipeStep(callable, extraArgs);
  }
}
