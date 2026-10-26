package io.beam.lang.gleam;

public record IntegerPattern(long value) implements Pattern {

  public static IntegerPattern of(long value) {
    return new IntegerPattern(value);
  }
}
