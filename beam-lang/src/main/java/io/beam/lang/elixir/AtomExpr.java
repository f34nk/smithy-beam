package io.beam.lang.elixir;

public record AtomExpr(String value) implements Expression {

  public static AtomExpr of(String value) {
    return new AtomExpr(value);
  }
}
