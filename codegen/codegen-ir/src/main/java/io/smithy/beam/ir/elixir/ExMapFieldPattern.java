package io.smithy.beam.ir.elixir;

public final class ExMapFieldPattern {
  private final ExExpr key;
  private final ExPattern pattern;

  public ExMapFieldPattern(ExExpr key, ExPattern pattern) {
    this.key = key;
    this.pattern = pattern;
  }

  public static ExMapFieldPattern field(ExExpr key, ExPattern pattern) {
    return new ExMapFieldPattern(key, pattern);
  }

  public ExExpr key() {
    return key;
  }

  public ExPattern pattern() {
    return pattern;
  }

  public String asString() {
    return key.asString() + " => " + pattern.asString();
  }
}
