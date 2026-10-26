package io.beam.lang.gleam;

import java.util.List;

public record TupleExpr(List<Expression> elements) implements Expression {

  public static TupleExpr of(List<Expression> elements) {
    return new TupleExpr(List.copyOf(elements));
  }
}
