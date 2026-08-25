package io.beam.dsl.elixir;

public record BinarySegmentExpr(Expression value, String typeOrNull) {

  public static BinarySegmentExpr of(Expression value) {
    return new BinarySegmentExpr(value, null);
  }

  public static BinarySegmentExpr of(Expression value, String type) {
    return new BinarySegmentExpr(value, type);
  }
}
