package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExAtomPattern implements ExPattern {
  private final String value;

  private ExAtomPattern(String value) {
    this.value = value;
  }

  public static ExAtomPattern atom(String value) {
    return new ExAtomPattern(value);
  }

  public String value() {
    return value;
  }

  @Override
  public List<String> lines() {
    return List.of(ExAtom.atom(value).asString());
  }
}
