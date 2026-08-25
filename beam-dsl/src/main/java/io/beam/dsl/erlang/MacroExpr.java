package io.beam.dsl.erlang;

public record MacroExpr(String name) implements Expression {

  public static MacroExpr of(String name) {
    return new MacroExpr(name);
  }
}
