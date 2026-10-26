package io.beam.lang.gleam;

/** {@code use pattern <- callback} sugar. */
public record UseExpr(Pattern pattern, Expression callback) implements Expression {

  public static UseExpr of(Pattern pattern, Expression callback) {
    return new UseExpr(pattern, callback);
  }

  public static UseExpr of(String name, Expression callback) {
    return new UseExpr(VariablePattern.of(name), callback);
  }
}
