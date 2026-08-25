package io.beam.dsl.erlang;

import java.util.List;

public record FunClause(List<Pattern> patterns, Guard guard, Expression body) {

  public static FunClause of(Pattern pattern, Expression body) {
    return of(List.of(pattern), body);
  }

  public static FunClause of(Pattern pattern, Guard guard, Expression body) {
    return of(List.of(pattern), guard, body);
  }

  public static FunClause of(List<Pattern> patterns, Expression body) {
    return new FunClause(patterns, null, body);
  }

  public static FunClause of(List<Pattern> patterns, Guard guard, Expression body) {
    return new FunClause(patterns, guard, body);
  }
}
