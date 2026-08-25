package io.beam.dsl.erlang;

import java.util.List;

public record IfExpr(List<IfClause> clauses) implements Expression {

  public static IfExpr of(List<IfClause> clauses) {
    return new IfExpr(clauses);
  }
}
