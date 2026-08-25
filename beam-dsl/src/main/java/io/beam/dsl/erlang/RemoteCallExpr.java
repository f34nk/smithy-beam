package io.beam.dsl.erlang;

import java.util.List;

public record RemoteCallExpr(Expression module, Expression function, List<Expression> arguments)
    implements Expression {

  public static RemoteCallExpr of(String module, String function, List<Expression> arguments) {
    return of(AtomExpr.of(module), AtomExpr.of(function), arguments);
  }

  public static RemoteCallExpr of(
      Expression module, Expression function, List<Expression> arguments) {
    return new RemoteCallExpr(module, function, arguments);
  }
}
