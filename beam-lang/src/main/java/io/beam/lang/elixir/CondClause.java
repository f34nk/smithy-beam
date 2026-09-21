package io.beam.lang.elixir;

public record CondClause(Expression condition, Expression body) {

  public static CondClause of(Expression condition, Expression body) {
    return new CondClause(condition, body);
  }
}
