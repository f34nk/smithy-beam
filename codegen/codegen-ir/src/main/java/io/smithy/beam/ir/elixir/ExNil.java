package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExNil implements ExExpr {
  private static final ExNil INSTANCE = new ExNil();

  private ExNil() {}

  public static ExNil nil() {
    return INSTANCE;
  }

  @Override
  public List<String> lines() {
    return List.of("nil");
  }
}
