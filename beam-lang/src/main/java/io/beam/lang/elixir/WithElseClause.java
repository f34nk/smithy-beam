package io.beam.lang.elixir;

public record WithElseClause(Pattern pattern, Expression body) {

  public static WithElseClause of(Pattern pattern, Expression body) {
    return new WithElseClause(pattern, body);
  }
}
