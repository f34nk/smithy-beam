package io.beam.dsl.elixir;

public record ConsListPattern(Pattern head, Pattern tail) implements Pattern {

  public static ConsListPattern of(Pattern head, Pattern tail) {
    return new ConsListPattern(head, tail);
  }
}
