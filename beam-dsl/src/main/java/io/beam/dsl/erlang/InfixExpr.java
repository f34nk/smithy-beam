package io.beam.dsl.erlang;

public record InfixExpr(Expression left, String operator, Expression right) implements Expression {

  public static InfixExpr of(Expression left, String operator, Expression right) {
    return new InfixExpr(left, operator, right);
  }
}
