package io.beam.lang.erlang;

import java.util.List;

public record TupleExpr(List<Expression> elements) implements Expression {

  public static TupleExpr of(List<Expression> elements) {
    return new TupleExpr(elements);
  }
}
