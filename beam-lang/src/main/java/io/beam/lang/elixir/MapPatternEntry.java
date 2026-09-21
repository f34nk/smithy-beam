package io.beam.lang.elixir;

public record MapPatternEntry(Expression key, Pattern value) {

  public static MapPatternEntry of(Expression key, Pattern value) {
    return new MapPatternEntry(key, value);
  }
}
