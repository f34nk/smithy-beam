package io.beam.dsl.elixir;

public record MatchExpr(Pattern pattern, Expression value, Expression bodyOrNull)
    implements Expression {

  public static MatchExpr bind(String name, Expression value) {
    return bind(VariablePattern.of(name), value);
  }

  public static MatchExpr bind(Pattern pattern, Expression value) {
    return new MatchExpr(pattern, value, null);
  }

  public static MatchExpr bind(String name, Expression value, Expression body) {
    return bind(VariablePattern.of(name), value, body);
  }

  public static MatchExpr bind(Pattern pattern, Expression value, Expression body) {
    return new MatchExpr(pattern, value, body);
  }
}
