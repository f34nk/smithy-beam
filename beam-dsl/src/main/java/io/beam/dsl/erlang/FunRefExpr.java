package io.beam.dsl.erlang;

public record FunRefExpr(String name, int arity) implements Expression {

  public static FunRefExpr of(String name, int arity) {
    return new FunRefExpr(name, arity);
  }
}
