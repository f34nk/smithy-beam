package io.beam.dsl.elixir;

public record InterpolatedLiteral(String text) implements InterpolatedSegment {

  public static InterpolatedLiteral of(String text) {
    return new InterpolatedLiteral(text);
  }
}
