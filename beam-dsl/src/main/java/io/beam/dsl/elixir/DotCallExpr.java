package io.beam.dsl.elixir;

import java.util.List;

public record DotCallExpr(Expression receiver, String function, List<Expression> args)
    implements Expression {

  public static DotCallExpr of(Expression receiver, String function, List<Expression> args) {
    return new DotCallExpr(receiver, function, args);
  }
}
