package io.beam.dsl.elixir;

public record RaiseExpr(Expression exception, Expression messageOrNull, boolean parenthesized)
    implements Expression {

  public static RaiseExpr of(Expression exception, Expression message) {
    return new RaiseExpr(exception, message, false);
  }

  public static RaiseExpr parenthesized(Expression exception, Expression message) {
    return new RaiseExpr(exception, message, true);
  }
}
