package io.beam.dsl.elixir;

public record InterpolatedExpr(Expression expression) implements InterpolatedSegment {

  public static InterpolatedExpr of(Expression expression) {
    return new InterpolatedExpr(expression);
  }
}
