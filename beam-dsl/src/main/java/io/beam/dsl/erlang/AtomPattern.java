package io.beam.dsl.erlang;

public record AtomPattern(String value) implements Pattern {

  public static AtomPattern of(String value) {
    return new AtomPattern(value);
  }
}
