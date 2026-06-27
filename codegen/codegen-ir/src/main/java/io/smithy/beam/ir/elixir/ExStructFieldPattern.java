package io.smithy.beam.ir.elixir;

public final class ExStructFieldPattern {
  private final String name;
  private final ExPattern patternOrNull;

  public ExStructFieldPattern(String name, ExPattern patternOrNull) {
    this.name = name;
    this.patternOrNull = patternOrNull;
  }

  public static ExStructFieldPattern fieldPattern(String name, ExPattern pattern) {
    return new ExStructFieldPattern(name, pattern);
  }

  /** Positional struct field: renders as the field name alone. */
  public static ExStructFieldPattern field(String name) {
    return new ExStructFieldPattern(name, null);
  }

  public String name() {
    return name;
  }

  public ExPattern patternOrNull() {
    return patternOrNull;
  }

  public String asString() {
    if (patternOrNull == null) {
      return name;
    }
    return name + ": " + patternOrNull.asString();
  }
}
