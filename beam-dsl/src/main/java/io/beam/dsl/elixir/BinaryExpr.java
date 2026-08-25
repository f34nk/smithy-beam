package io.beam.dsl.elixir;

import java.util.List;

public record BinaryExpr(List<BinarySegmentExpr> segments) implements Expression {

  public static BinaryExpr of(List<BinarySegmentExpr> segments) {
    return new BinaryExpr(segments);
  }
}
