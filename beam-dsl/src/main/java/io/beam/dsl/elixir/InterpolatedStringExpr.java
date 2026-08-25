package io.beam.dsl.elixir;

import java.util.List;

public record InterpolatedStringExpr(List<InterpolatedSegment> segments) implements Expression {

  public static InterpolatedStringExpr of(List<InterpolatedSegment> segments) {
    return new InterpolatedStringExpr(segments);
  }
}
