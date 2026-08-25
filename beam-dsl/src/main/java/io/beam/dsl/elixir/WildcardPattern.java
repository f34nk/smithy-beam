package io.beam.dsl.elixir;

public record WildcardPattern() implements Pattern {

  public static WildcardPattern of() {
    return new WildcardPattern();
  }
}
