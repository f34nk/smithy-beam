package io.beam.lang.gleam;

public record StringPattern(String value) implements Pattern {

  public static StringPattern of(String value) {
    return new StringPattern(value);
  }
}
