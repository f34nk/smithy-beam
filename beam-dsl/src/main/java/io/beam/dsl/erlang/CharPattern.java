package io.beam.dsl.erlang;

public record CharPattern(char value) implements Pattern {

  public static CharPattern of(char value) {
    return new CharPattern(value);
  }
}
