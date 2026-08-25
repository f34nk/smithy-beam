package io.beam.dsl.elixir;

public record NilExpr() implements Expression {

  public static NilExpr of() {
    return new NilExpr();
  }
}
