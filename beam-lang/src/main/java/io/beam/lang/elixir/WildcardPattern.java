package io.beam.lang.elixir;

public record WildcardPattern() implements Pattern {

  public static WildcardPattern of() {
    return new WildcardPattern();
  }
}
