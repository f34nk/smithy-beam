package io.beam.dsl.erlang;

public record MatchExpr(Pattern pattern, Expression value, Expression body) implements Expression {

  public static MatchExpr of(Pattern pattern, Expression value, Expression body) {
    return new MatchExpr(pattern, value, body);
  }

  public static MatchExpr bind(String name, Expression value) {
    return bind(name, value, null);
  }

  public static MatchExpr bind(String name, Expression value, Expression body) {
    return of(VariablePattern.of(name), value, body);
  }

  public static MatchExpr bindValue(String name, Expression value) {
    return of(VariablePattern.of(name), value, null);
  }
}
