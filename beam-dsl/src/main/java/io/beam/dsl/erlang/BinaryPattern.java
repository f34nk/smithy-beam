package io.beam.dsl.erlang;

import java.util.List;

public record BinaryPattern(List<BinarySegmentPattern> segments) implements Pattern {

  public static BinaryPattern of(String value) {
    if (value.isEmpty()) {
      return new BinaryPattern(List.of());
    }
    return new BinaryPattern(List.of(BinarySegmentPattern.literal(value)));
  }

  public static BinaryPattern of(List<BinarySegmentPattern> segments) {
    return new BinaryPattern(segments);
  }
}
