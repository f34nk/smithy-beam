package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExOp implements ExExpr {
  private final String operator;
  private final ExExpr left;
  private final ExExpr right;

  public ExOp(String operator, ExExpr left, ExExpr right) {
    this.operator = operator;
    this.left = left;
    this.right = right;
  }

  private ExOp(String prefixOperator, ExExpr operand) {
    this.operator = prefixOperator;
    this.left = operand;
    this.right = null;
  }

  public static ExOp op(String operator, ExExpr left, ExExpr right) {
    return new ExOp(operator, left, right);
  }

  public static ExOp prefix(String operator, ExExpr operand) {
    return new ExOp(operator, operand);
  }

  public String operator() {
    return operator;
  }

  public ExExpr left() {
    return left;
  }

  public ExExpr right() {
    return right;
  }

  @Override
  public List<String> lines() {
    if (right == null) {
      return List.of(operator + " " + left.asString());
    }
    return List.of(left.asString() + " " + operator + " " + right.asString());
  }
}
