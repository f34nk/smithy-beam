package io.beam.dsl.erlang;

import java.util.List;

public record BinaryExpr(List<BinarySegmentExpr> segments) implements Expression {

  public static BinaryExpr of(String value) {
    if (value.isEmpty()) {
      return new BinaryExpr(List.of());
    }
    return new BinaryExpr(List.of(BinarySegmentExpr.literal(value)));
  }

  public static BinaryExpr of(List<BinarySegmentExpr> segments) {
    return new BinaryExpr(segments);
  }
}
