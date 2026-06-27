package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlMacro implements ErlExpr {
  private final String name;

  public ErlMacro(String name) {
    this.name = name;
  }

  public static ErlMacro macro(String name) {
    return new ErlMacro(name);
  }

  public String name() {
    return name;
  }

  @Override
  public List<String> lines() {
    return List.of("?" + name);
  }
}
