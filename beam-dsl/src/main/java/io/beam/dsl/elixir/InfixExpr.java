package io.beam.dsl.elixir;

public record InfixExpr(Expression left, String op, Expression right) implements Expression {

  public static InfixExpr of(Expression left, String op, Expression right) {
    return new InfixExpr(left, op, right);
  }
}
