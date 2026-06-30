package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExBinaryConcatPattern implements ExPattern {
  private final ExPattern left;
  private final ExPattern right;

  public ExBinaryConcatPattern(ExPattern left, ExPattern right) {
    this.left = left;
    this.right = right;
  }

  public static ExBinaryConcatPattern concat(ExPattern left, ExPattern right) {
    return new ExBinaryConcatPattern(left, right);
  }

  public ExPattern left() {
    return left;
  }

  public ExPattern right() {
    return right;
  }

  @Override
  public List<String> lines() {
    return List.of(left.asString() + " <> " + right.asString());
  }
}
