package io.beam.dsl.elixir;

public record AssignPattern(Pattern left, Pattern right) implements Pattern {

  public static AssignPattern of(String name, Pattern pattern) {
    return new AssignPattern(VariablePattern.of(name), pattern);
  }

  public static AssignPattern of(Pattern left, Pattern right) {
    return new AssignPattern(left, right);
  }
}
