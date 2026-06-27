package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlApply implements ErlExpr {
  private final ErlExpr function;
  private final List<ErlExpr> args;

  public ErlApply(ErlExpr function, List<ErlExpr> args) {
    this.function = function;
    this.args = List.copyOf(args);
  }

  public static ErlApply apply(ErlExpr function, ErlExpr... args) {
    return new ErlApply(function, List.of(args));
  }

  public ErlExpr function() {
    return function;
  }

  public List<ErlExpr> args() {
    return args;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder(function.asString()).append('(');
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
