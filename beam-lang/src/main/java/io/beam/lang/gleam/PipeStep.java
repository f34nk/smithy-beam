package io.beam.lang.gleam;

import java.util.List;

/**
 * One pipe step. {@code callable} is typically a {@link LocalCallExpr} or {@link RemoteCallExpr}
 * whose first argument is the pipe subject (omitted in source).
 */
public record PipeStep(Expression callable, List<Expression> extraArgs) {

  public static PipeStep of(Expression callable) {
    return new PipeStep(callable, List.of());
  }

  public static PipeStep of(Expression callable, List<Expression> extraArgs) {
    return new PipeStep(callable, List.copyOf(extraArgs));
  }
}
