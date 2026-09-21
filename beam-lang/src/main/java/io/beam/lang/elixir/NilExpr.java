package io.beam.lang.elixir;

public record NilExpr() implements Expression {

  public static NilExpr of() {
    return new NilExpr();
  }
}
