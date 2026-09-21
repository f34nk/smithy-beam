package io.beam.lang.elixir;

public record StructPatternField(String nameOrNull, Pattern pattern) {

  public static StructPatternField of(String name, Pattern pattern) {
    return new StructPatternField(name, pattern);
  }
}
