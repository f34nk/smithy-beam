package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExGuard implements IrObject {
  private final String functionOrNull;
  private final List<ExExpr> args;
  private final String exprOrNull;

  public ExGuard(String function, List<ExExpr> args) {
    this.functionOrNull = function;
    this.args = List.copyOf(args);
    this.exprOrNull = null;
  }

  private ExGuard(String expr) {
    this.functionOrNull = null;
    this.args = List.of();
    this.exprOrNull = expr;
  }

  public static ExGuard guard(String name, ExExpr... args) {
    return new ExGuard(name, List.of(args));
  }

  public static ExGuard exprGuard(ExExpr expr) {
    return new ExGuard(expr.asString());
  }

  public String function() {
    return functionOrNull;
  }

  public List<ExExpr> args() {
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
