package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExNilPattern implements ExPattern {
  private static final ExNilPattern INSTANCE = new ExNilPattern();

  private ExNilPattern() {}

  public static ExNilPattern nil() {
    return INSTANCE;
  }

  @Override
  public List<String> lines() {
    return List.of("nil");
  }
}
