package io.beam.lang.gleam;

import java.util.List;

/** Qualified call such as {@code json.string(value)} or {@code list.map(xs, f)}. */
public record RemoteCallExpr(String module, String function, List<Expression> args)
    implements Expression {

  public static RemoteCallExpr of(String module, String function, List<Expression> args) {
    return new RemoteCallExpr(module, function, List.copyOf(args));
  }
}
