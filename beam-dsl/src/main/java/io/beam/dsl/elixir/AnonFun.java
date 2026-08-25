package io.beam.dsl.elixir;

import java.util.List;

public record AnonFun(List<AnonFunClause> clauses) implements Expression {

  public static AnonFun of(List<AnonFunClause> clauses) {
    return new AnonFun(clauses);
  }
}
