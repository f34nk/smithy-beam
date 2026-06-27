package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExInteger implements ExExpr {
  private final long value;

  private ExInteger(long value) {
    this.value = value;
  }

  public static ExInteger integer(long value) {
    return new ExInteger(value);
  }

  public long value() {
    return value;
  }

  @Override
  public List<String> lines() {
    return List.of(Long.toString(value));
  }
}
