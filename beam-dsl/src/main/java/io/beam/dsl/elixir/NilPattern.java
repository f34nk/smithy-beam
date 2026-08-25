package io.beam.dsl.elixir;

public record NilPattern() implements Pattern {

  public static NilPattern of() {
    return new NilPattern();
  }
}
