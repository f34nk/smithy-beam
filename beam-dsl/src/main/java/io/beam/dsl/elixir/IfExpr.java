package io.beam.dsl.elixir;

public record IfExpr(
    Expression condition, Expression thenBranch, Expression elseBranchOrNull, boolean inline)
    implements Expression {

  public static IfExpr of(Expression condition, Expression thenBranch, Expression elseBranch) {
    return new IfExpr(condition, thenBranch, elseBranch, false);
  }

  public static IfExpr of(
      Expression condition, Expression thenBranch, Expression elseBranch, boolean inline) {
    return new IfExpr(condition, thenBranch, elseBranch, inline);
  }

  public static IfExpr inline(Expression condition, Expression thenBranch, Expression elseBranch) {
    return new IfExpr(condition, thenBranch, elseBranch, true);
  }
}
