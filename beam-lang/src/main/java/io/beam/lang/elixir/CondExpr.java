package io.beam.lang.elixir;

import java.util.List;

public record CondExpr(List<CondClause> clauses) implements Expression {

  public static CondExpr of(List<CondClause> clauses) {
    return new CondExpr(List.copyOf(clauses));
  }
}
