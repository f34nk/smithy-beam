package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExAtom implements ExExpr {
  private final String value;

  private ExAtom(String value) {
    this.value = value;
  }

  public static ExAtom atom(String value) {
    return new ExAtom(value);
  }

  public String value() {
    return value;
  }

  @Override
  public List<String> lines() {
    return List.of(ExLayout.renderAtom(value));
  }
}
