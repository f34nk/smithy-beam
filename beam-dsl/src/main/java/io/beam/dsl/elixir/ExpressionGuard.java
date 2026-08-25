package io.beam.dsl.elixir;

public record ExpressionGuard(Expression expression) implements Guard {

  public static ExpressionGuard of(Expression expression) {
    return new ExpressionGuard(expression);
  }
}
