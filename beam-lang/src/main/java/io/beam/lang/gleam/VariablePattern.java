package io.beam.lang.gleam;

public record VariablePattern(String name) implements Pattern {

  public static VariablePattern of(String name) {
    return new VariablePattern(name);
  }
}
