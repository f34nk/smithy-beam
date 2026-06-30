package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExDotCall implements ExExpr {
  private final ExExpr target;
  private final List<ExExpr> args;

  public ExDotCall(ExExpr target, List<ExExpr> args) {
    this.target = target;
    this.args = List.copyOf(args);
  }

  public static ExDotCall dotCall(ExExpr target, ExExpr... args) {
    return new ExDotCall(target, List.of(args));
  }

  public ExExpr target() {
    return target;
  }

  public List<ExExpr> args() {
    return args;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder(target.asString()).append(".(");
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
