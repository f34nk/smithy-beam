package io.beam.lang.erlang;

import java.util.List;

public record Fun(List<FunClause> clauses) implements Expression {

  public static Fun of(List<FunClause> clauses) {
    return new Fun(clauses);
  }
}
