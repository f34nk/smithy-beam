package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExVar implements ExExpr {
  private final String name;

  private ExVar(String name) {
    this.name = name;
  }

  public static ExVar var(String name) {
    return new ExVar(name);
  }

  public String name() {
    return name;
  }

  @Override
  public List<String> lines() {
    return List.of(name);
  }
}
