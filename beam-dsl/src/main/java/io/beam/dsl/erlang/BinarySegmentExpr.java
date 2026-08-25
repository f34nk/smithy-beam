package io.beam.dsl.erlang;

/**
 * Binary segment. When {@code literal} is set, it is raw content; the renderer escapes for the
 * target language.
 */
public record BinarySegmentExpr(
    Expression expression, String literal, Integer size, String type, Integer unit) {

  public static BinarySegmentExpr literal(String literal) {
    return new BinarySegmentExpr(null, literal, null, null, null);
  }

  public static BinarySegmentExpr of(Expression expression) {
    return new BinarySegmentExpr(expression, null, null, null, null);
  }

  public static BinarySegmentExpr of(Expression expression, String type) {
    return new BinarySegmentExpr(expression, null, null, type, null);
  }

  public static BinarySegmentExpr of(Expression expression, Integer size, String type) {
    return new BinarySegmentExpr(expression, null, size, type, null);
  }

  public static BinarySegmentExpr of(
      Expression expression, Integer size, String type, Integer unit) {
    return new BinarySegmentExpr(expression, null, size, type, unit);
  }
}
