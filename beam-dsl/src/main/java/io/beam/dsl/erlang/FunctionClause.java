package io.beam.dsl.erlang;

import java.util.List;

public record FunctionClause(List<Pattern> patterns, Guard guard, Expression body) {

  public static FunctionClause of(List<Pattern> patterns, Expression body) {
    return new FunctionClause(patterns, null, body);
  }

  public static FunctionClause of(List<Pattern> patterns, Guard guard, Expression body) {
    return new FunctionClause(patterns, guard, body);
  }
}
