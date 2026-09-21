package io.beam.lang.elixir;

public record InterpolatedLiteral(String text) implements InterpolatedSegment {

  public static InterpolatedLiteral of(String text) {
    return new InterpolatedLiteral(text);
  }
}
