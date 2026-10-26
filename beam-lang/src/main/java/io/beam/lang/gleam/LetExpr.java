package io.beam.lang.gleam;

/** {@code let pattern = value} binding. */
public record LetExpr(Pattern pattern, Expression value) implements Expression {

  public static LetExpr of(Pattern pattern, Expression value) {
    return new LetExpr(pattern, value);
  }

  public static LetExpr of(String name, Expression value) {
    return new LetExpr(VariablePattern.of(name), value);
  }
}
