package io.beam.dsl.erlang;

import java.util.List;

public record ListExpr(List<Expression> elements, Expression tail) implements Expression {

  public static ListExpr of(List<Expression> elements) {
    return new ListExpr(elements, null);
  }

  public static ListExpr of(List<Expression> elements, Expression tail) {
    return new ListExpr(elements, tail);
  }
}
