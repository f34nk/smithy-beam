package io.beam.dsl.elixir;

public record WithElseClause(Pattern pattern, Expression body) {

  public static WithElseClause of(Pattern pattern, Expression body) {
    return new WithElseClause(pattern, body);
  }
}
