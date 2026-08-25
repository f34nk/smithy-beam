package io.beam.dsl.elixir;

public record WithBinding(Pattern pattern, Expression expression) {

  public static WithBinding of(Pattern pattern, Expression expression) {
    return new WithBinding(pattern, expression);
  }
}
