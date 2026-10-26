package io.beam.lang.gleam;

public record CaseClause(Pattern pattern, Expression body) {

  public static CaseClause of(Pattern pattern, Expression body) {
    return new CaseClause(pattern, body);
  }
}
