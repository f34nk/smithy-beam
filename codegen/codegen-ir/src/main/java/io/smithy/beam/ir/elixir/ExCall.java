package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExCall implements ExExpr {
  private final String module;
  private final String function;
  private final List<ExExpr> args;

  public ExCall(String module, String function, List<ExExpr> args) {
    this.module = module;
    this.function = function;
    this.args = List.copyOf(args);
  }

  public static ExCall call(String module, String function, ExExpr... args) {
    return new ExCall(module, function, List.of(args));
  }

  public String module() {
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
    StringBuilder sb = new StringBuilder();
    sb.append(module).append('.').append(function).append('(');
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
