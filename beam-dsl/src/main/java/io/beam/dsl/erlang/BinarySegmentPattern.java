package io.beam.dsl.erlang;

public record BinarySegmentPattern(
    Pattern pattern, String literal, Integer size, String type, Integer unit) {

  public static BinarySegmentPattern literal(String literal) {
    return new BinarySegmentPattern(null, literal, null, null, null);
  }

  public static BinarySegmentPattern of(Pattern pattern) {
    return new BinarySegmentPattern(pattern, null, null, null, null);
  }

  public static BinarySegmentPattern of(Pattern pattern, String type) {
    return new BinarySegmentPattern(pattern, null, null, type, null);
  }

  public static BinarySegmentPattern of(Pattern pattern, Integer size, String type) {
    return new BinarySegmentPattern(pattern, null, size, type, null);
  }

  public static BinarySegmentPattern of(Pattern pattern, Integer size, String type, Integer unit) {
    return new BinarySegmentPattern(pattern, null, size, type, unit);
  }
}
