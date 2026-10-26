package io.beam.lang.gleam;

import java.util.List;

public record ListExpr(List<Expression> elements) implements Expression {

  public static ListExpr of(List<Expression> elements) {
    return new ListExpr(List.copyOf(elements));
  }
}
