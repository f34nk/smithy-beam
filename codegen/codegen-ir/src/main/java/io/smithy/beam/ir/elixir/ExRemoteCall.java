package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExRemoteCall implements ExExpr {
  private final ExExpr module;
  private final String function;
  private final List<ExExpr> args;

  public ExRemoteCall(ExExpr module, String function, List<ExExpr> args) {
    this.module = module;
    this.function = function;
    this.args = List.copyOf(args);
  }

  public static ExRemoteCall call(ExExpr module, String function, ExExpr... args) {
    return new ExRemoteCall(module, function, List.of(args));
  }

  public ExExpr module() {
    return module;
  }

  public String function() {
    return function;
  }

  public List<ExExpr> args() {
    return args;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder(module.asString()).append('.').append(function).append('(');
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
