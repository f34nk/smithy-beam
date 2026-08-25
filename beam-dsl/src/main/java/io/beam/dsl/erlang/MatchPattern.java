package io.beam.dsl.erlang;

public record MatchPattern(Pattern left, Pattern right) implements Pattern {

  public static MatchPattern of(Pattern left, Pattern right) {
    return new MatchPattern(left, right);
  }
}
