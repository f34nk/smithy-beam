package io.beam.lang.elixir;

public record NilPattern() implements Pattern {

  public static NilPattern of() {
    return new NilPattern();
  }
}
