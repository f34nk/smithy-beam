package io.beam.dsl.elixir;

public record PinPattern(String name) implements Pattern {

  public static PinPattern of(String name) {
    return new PinPattern(name);
  }
}
