package io.beam.lang.gleam;

import java.util.List;

public record LocalCallExpr(String function, List<Expression> args) implements Expression {

  public static LocalCallExpr of(String function, List<Expression> args) {
    return new LocalCallExpr(function, List.copyOf(args));
  }
}
