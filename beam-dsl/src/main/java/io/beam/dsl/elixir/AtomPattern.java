package io.beam.dsl.elixir;

public record AtomPattern(String value) implements Pattern {

  public static AtomPattern of(String value) {
    return new AtomPattern(value);
  }
}
