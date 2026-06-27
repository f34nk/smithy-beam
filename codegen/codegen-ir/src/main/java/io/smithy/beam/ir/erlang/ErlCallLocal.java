package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlCallLocal implements ErlExpr {
  private final String function;
  private final List<ErlExpr> args;

  public ErlCallLocal(String function, List<ErlExpr> args) {
    this.function = function;
    this.args = List.copyOf(args);
  }

  public static ErlCallLocal callLocal(String function, ErlExpr... args) {
    return new ErlCallLocal(function, List.of(args));
  }

  public String function() {
    return function;
  }

  public List<ErlExpr> args() {
    return args;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    return ErlFormat.formatPrefixedCall(indent, function, args, "");
  }
}
