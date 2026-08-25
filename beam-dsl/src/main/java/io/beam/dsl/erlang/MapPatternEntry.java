package io.beam.dsl.erlang;

public record MapPatternEntry(Expression key, Pattern value, boolean updateOnly) {

  public static MapPatternEntry of(Expression key, Pattern value) {
    return new MapPatternEntry(key, value, false);
  }

  public static MapPatternEntry of(Expression key, Pattern value, boolean updateOnly) {
    return new MapPatternEntry(key, value, updateOnly);
  }
}
