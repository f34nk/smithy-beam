package io.beam.lang.erlang;

public record ExpressionGuard(Expression expression) implements Guard {

  public static ExpressionGuard of(Expression expression) {
    return new ExpressionGuard(expression);
  }
}
