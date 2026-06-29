package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExPinPattern implements ExPattern {
  private final ExPattern left;
  private final ExPattern right;

  public ExPinPattern(ExPattern left, ExPattern right) {
    this.left = left;
    this.right = right;
  }

  public static ExPinPattern pin(ExPattern left, ExPattern right) {
    return new ExPinPattern(left, right);
  }

  public ExPattern left() {
    return left;
  }

  public ExPattern right() {
    return right;
  }

  @Override
  public List<String> lines() {
    return List.of(left.asString() + " = " + right.asString());
  }
}
