package io.beam.dsl.elixir;

public record ComparisonGuard(Expression left, String op, Expression right) implements Guard {

  public static ComparisonGuard of(Expression left, String op, Expression right) {
    return new ComparisonGuard(left, op, right);
  }
}
