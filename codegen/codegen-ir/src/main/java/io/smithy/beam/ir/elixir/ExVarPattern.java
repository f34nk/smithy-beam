package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExVarPattern implements ExPattern {
  private final String name;

  private ExVarPattern(String name) {
    this.name = name;
  }

  public static ExVarPattern var(String name) {
    return new ExVarPattern(name);
  }

  public String name() {
    return name;
  }

  @Override
  public List<String> lines() {
    return List.of(name);
  }
}
