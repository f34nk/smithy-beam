package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExStringPattern implements ExPattern {
  private final String value;

  private ExStringPattern(String value) {
    this.value = value;
  }

  public static ExStringPattern string(String value) {
    return new ExStringPattern(value);
  }

  public String value() {
    return value;
  }

  @Override
  public List<String> lines() {
    return List.of(ExString.renderString(value));
  }
}
