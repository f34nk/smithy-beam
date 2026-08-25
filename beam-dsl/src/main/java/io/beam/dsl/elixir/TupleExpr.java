package io.beam.dsl.elixir;

import java.util.List;

public record TupleExpr(List<Expression> elements) implements Expression {

  public static TupleExpr of(List<Expression> elements) {
    return new TupleExpr(elements);
  }
}
