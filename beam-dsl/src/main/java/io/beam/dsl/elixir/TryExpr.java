package io.beam.dsl.elixir;

import java.util.List;

public record TryExpr(Expression body, List<CatchClause> catchClauses) implements Expression {

  public static TryExpr of(Expression body, List<CatchClause> catchClauses) {
    return new TryExpr(body, catchClauses);
  }
}
