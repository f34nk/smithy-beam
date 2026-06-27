package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
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

  public static ExCall filtermap(ExAnonymousFn fun, ExExpr listArg) {
    return new ExCall("Enum", "filter_map", List.of(listArg, fun));
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
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (isFiltermap()) {
      List<String> out = new ArrayList<>();
      out.add(IrObject.indent(indent) + "Enum.filter_map(");
      out.add(IrObject.indent(indent + 1) + args.get(0).asString() + ",");
      List<String> funLines = new ArrayList<>(((ExAnonymousFn) args.get(1)).inlineClauseLines(indent + 1));
      out.addAll(funLines);
      out.add(IrObject.indent(indent) + ")");
      return out;
    }
    return List.of(IrObject.indent(indent) + inlineAsString());
  }

  private boolean isFiltermap() {
    return "filter_map".equals(function)
        && "Enum".equals(module)
        && args.size() == 2
        && args.get(1) instanceof ExAnonymousFn;
  }

  private String inlineAsString() {
    StringBuilder sb = new StringBuilder();
    sb.append(module).append('.').append(function).append('(');
    for (int i = 0; i < args.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(args.get(i).asString());
    }
    sb.append(')');
    return sb.toString();
  }
}
