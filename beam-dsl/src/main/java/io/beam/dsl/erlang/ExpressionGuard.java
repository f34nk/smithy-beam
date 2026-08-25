package io.beam.dsl.erlang;

public record ExpressionGuard(Expression expression) implements Guard {

  public static ExpressionGuard of(Expression expression) {
    return new ExpressionGuard(expression);
  }
}
