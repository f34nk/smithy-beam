package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlGuard implements ErlExpr {
  private final String functionOrNull;
  private final List<ErlExpr> args;
  private final String exprOrNull;

  public ErlGuard(String function, List<ErlExpr> args) {
    this.functionOrNull = function;
    this.args = List.copyOf(args);
    this.exprOrNull = null;
  }

  private ErlGuard(String expr) {
    this.functionOrNull = null;
    this.args = List.of();
    this.exprOrNull = expr;
  }

  public static ErlGuard guard(String function, ErlExpr... args) {
    return new ErlGuard(function, List.of(args));
  }

  public static ErlGuard exprGuard(ErlExpr expr) {
    return new ErlGuard(expr.asString());
  }

  public String function() {
    return functionOrNull;
  }

  public List<ErlExpr> args() {
    return args;
  }

  @Override
  public List<String> lines() {
    if (exprOrNull != null) {
      return List.of(exprOrNull);
    }
    StringBuilder sb = new StringBuilder(functionOrNull).append('(');
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
