package io.beam.dsl.elixir;

import java.util.List;

public record CaseExpr(Expression subjectOrNull, List<Clause> clauses) implements Expression {

  public static CaseExpr of(Expression subject, List<Clause> clauses) {
    return new CaseExpr(subject, clauses);
  }

  public static CaseExpr piped(List<Clause> clauses) {
    return new CaseExpr(null, clauses);
  }
}
