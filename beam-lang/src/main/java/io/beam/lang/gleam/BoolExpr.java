package io.beam.lang.gleam;

public record BoolExpr(boolean value) implements Expression {

  public static BoolExpr of(boolean value) {
    return new BoolExpr(value);
  }
}
