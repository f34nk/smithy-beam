package io.beam.dsl.elixir;

import java.util.List;

public record RemoteCallExpr(String module, String function, List<Expression> args)
    implements Expression {

  public static RemoteCallExpr of(String module, String function, List<Expression> args) {
    return new RemoteCallExpr(module, function, args);
  }
}
