package io.beam.lang.gleam;

/** String literal. {@code value} is raw content; the renderer escapes for Gleam. */
public record StringExpr(String value) implements Expression {

  public static StringExpr of(String value) {
    return new StringExpr(value);
  }
}
