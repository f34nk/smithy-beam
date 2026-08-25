package io.beam.dsl.elixir;

public record VariablePattern(String name) implements Pattern {

  public static VariablePattern of(String name) {
    return new VariablePattern(name);
  }
}
