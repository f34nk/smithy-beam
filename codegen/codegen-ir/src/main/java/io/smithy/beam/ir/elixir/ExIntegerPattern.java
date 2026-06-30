package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExIntegerPattern implements ExPattern {
  private final long value;

  private ExIntegerPattern(long value) {
    this.value = value;
  }

  public static ExIntegerPattern integer(long value) {
    return new ExIntegerPattern(value);
  }

  public long value() {
    return value;
  }

  @Override
  public List<String> lines() {
    return List.of(Long.toString(value));
  }
}
