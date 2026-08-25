package io.beam.dsl.erlang;

import java.util.List;

public record TryExpr(Expression body, List<Clause> catchClauses) implements Expression {

  public static TryExpr of(Expression body, List<Clause> catchClauses) {
    return new TryExpr(body, catchClauses);
  }
}
