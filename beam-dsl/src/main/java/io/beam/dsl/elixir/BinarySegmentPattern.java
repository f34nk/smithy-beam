package io.beam.dsl.elixir;

public record BinarySegmentPattern(Pattern patternOrNull, String literalOrNull, String typeOrNull) {

  public static BinarySegmentPattern literal(String literal) {
    return new BinarySegmentPattern(null, literal, null);
  }

  public static BinarySegmentPattern of(Pattern pattern, String type) {
    return new BinarySegmentPattern(pattern, null, type);
  }
}
