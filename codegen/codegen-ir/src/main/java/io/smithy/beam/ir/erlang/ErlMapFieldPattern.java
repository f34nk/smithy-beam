package io.smithy.beam.ir.erlang;

public final class ErlMapFieldPattern {
  private final String key;
  private final ErlPattern pattern;

  public ErlMapFieldPattern(String key, ErlPattern pattern) {
    this.key = key;
    this.pattern = pattern;
  }

  public static ErlMapFieldPattern fieldPattern(String key, ErlPattern pattern) {
    return new ErlMapFieldPattern(key, pattern);
  }

  public String asString() {
    return key + " := " + pattern.asString();
  }
}
