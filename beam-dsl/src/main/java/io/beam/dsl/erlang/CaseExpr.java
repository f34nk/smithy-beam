package io.beam.dsl.erlang;

import java.util.List;

public record CaseExpr(Expression expression, List<Clause> clauses) implements Expression {

  public static CaseExpr of(Expression expression, List<Clause> clauses) {
    return new CaseExpr(expression, clauses);
  }
}
