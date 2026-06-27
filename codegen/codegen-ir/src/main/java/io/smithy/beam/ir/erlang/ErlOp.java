package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlOp implements ErlExpr {
  private final String operator;
  private final ErlExpr left;
  private final ErlExpr right;

  public ErlOp(String operator, ErlExpr left, ErlExpr right) {
    this.operator = operator;
    this.left = left;
    this.right = right;
  }

  private ErlOp(String prefixOperator, ErlExpr operand) {
    this.operator = prefixOperator;
    this.left = operand;
    this.right = null;
  }

  public static ErlOp op(String operator, ErlExpr left, ErlExpr right) {
    return new ErlOp(operator, left, right);
  }

  public static ErlOp prefix(String operator, ErlExpr operand) {
    return new ErlOp(operator, operand);
  }

  public String operator() {
    return operator;
  }

  public ErlExpr left() {
    return left;
  }

  public ErlExpr right() {
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
