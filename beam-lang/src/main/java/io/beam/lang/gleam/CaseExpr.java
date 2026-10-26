package io.beam.lang.gleam;

import java.util.List;

public record CaseExpr(Expression subject, List<CaseClause> clauses) implements Expression {

  public static CaseExpr of(Expression subject, List<CaseClause> clauses) {
    return new CaseExpr(subject, List.copyOf(clauses));
  }
}
