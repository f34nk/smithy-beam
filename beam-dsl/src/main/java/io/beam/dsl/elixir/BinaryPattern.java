package io.beam.dsl.elixir;

import java.util.List;

public record BinaryPattern(List<BinarySegmentPattern> segments) implements Pattern {

  public static BinaryPattern of(List<BinarySegmentPattern> segments) {
    return new BinaryPattern(segments);
  }
}
