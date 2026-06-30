package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExCallLocal implements ExExpr {
  private final String function;
  private final List<ExExpr> args;

  public ExCallLocal(String function, List<ExExpr> args) {
    this.function = function;
    this.args = List.copyOf(args);
  }

  public static ExCallLocal callLocal(String function, ExExpr... args) {
    return new ExCallLocal(function, List.of(args));
  }

  public String function() {
    return function;
  }

  public List<ExExpr> args() {
    return args;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder(function).append('(');
    for (int i = 0; i < args.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(args.get(i).asString());
    }
    sb.append(')');
    return List.of(sb.toString());
  }
}
