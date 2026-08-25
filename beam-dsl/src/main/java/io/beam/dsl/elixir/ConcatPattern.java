package io.beam.dsl.elixir;

public record ConcatPattern(Pattern left, Pattern right) implements Pattern {

  public static ConcatPattern of(Pattern left, Pattern right) {
    return new ConcatPattern(left, right);
  }
}
