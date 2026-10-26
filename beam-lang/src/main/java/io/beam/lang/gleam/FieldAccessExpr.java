package io.beam.lang.gleam;

/** Field access such as {@code response.status}. */
public record FieldAccessExpr(Expression target, String field) implements Expression {

  public static FieldAccessExpr of(Expression target, String field) {
    return new FieldAccessExpr(target, field);
  }
}
