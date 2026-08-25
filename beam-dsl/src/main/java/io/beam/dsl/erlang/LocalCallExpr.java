package io.beam.dsl.erlang;

import java.util.List;

public record LocalCallExpr(String function, List<Expression> arguments) implements Expression {

  public static LocalCallExpr of(String function, List<Expression> arguments) {
    return new LocalCallExpr(function, arguments);
  }
}
