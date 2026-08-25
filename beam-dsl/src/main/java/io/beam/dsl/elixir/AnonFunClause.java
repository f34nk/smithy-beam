package io.beam.dsl.elixir;

import java.util.List;

public record AnonFunClause(List<Pattern> params, Guard guardOrNull, Expression body) {

  public static AnonFunClause of(List<Pattern> params, Expression body) {
    return new AnonFunClause(params, null, body);
  }

  public static AnonFunClause of(List<Pattern> params, Guard guard, Expression body) {
    return new AnonFunClause(params, guard, body);
  }
}
