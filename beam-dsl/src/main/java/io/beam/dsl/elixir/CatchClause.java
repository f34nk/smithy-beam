package io.beam.dsl.elixir;

public record CatchClause(Pattern kind, Pattern reason, Expression body) {

  public static CatchClause of(Pattern kind, Pattern reason, Expression body) {
    return new CatchClause(kind, reason, body);
  }
}
