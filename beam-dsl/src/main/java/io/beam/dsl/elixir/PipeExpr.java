package io.beam.dsl.elixir;

import java.util.List;

public record PipeExpr(Expression initial, List<PipeStep> steps) implements Expression {

  public static PipeExpr of(Expression initial, List<PipeStep> steps) {
    return new PipeExpr(initial, steps);
  }
}
