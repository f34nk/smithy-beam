package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlRemoteCall implements ErlExpr {
  private final ErlExpr module;
  private final ErlExpr function;
  private final List<ErlExpr> args;

  public ErlRemoteCall(ErlExpr module, ErlExpr function, List<ErlExpr> args) {
    this.module = module;
    this.function = function;
    this.args = List.copyOf(args);
  }

  public static ErlRemoteCall call(ErlExpr module, ErlExpr function, ErlExpr... args) {
    return new ErlRemoteCall(module, function, List.of(args));
  }

  public static ErlRemoteCall call(ErlExpr module, String function, ErlExpr... args) {
    return call(module, ErlAtom.atom(function), args);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    String prefix = module.asString() + ":" + function.asString();
    return ErlFormat.formatPrefixedCall(indent, prefix, args, "");
  }
}
