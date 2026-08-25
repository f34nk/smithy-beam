package io.beam.dsl.erlang;

public record NotExpr(Expression expression) implements Expression {

  public static NotExpr of(Expression expression) {
    return new NotExpr(expression);
  }
}
