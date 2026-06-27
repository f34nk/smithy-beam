package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
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
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (right == null) {
      return List.of(ErlFormat.prefixed(indent, operator + " " + left.asString()));
    }
    String inline = left.asString() + " " + operator + " " + right.asString();
    if (!ErlFormat.exceedsLineLimit(indent, inline)) {
      return List.of(ErlFormat.prefixed(indent, inline));
    }
    if ("++".equals(operator)) {
      List<String> out = new ArrayList<>();
      List<String> leftLines = ErlFormat.renderExprLines(left, indent);
      if (leftLines.size() == 1) {
        out.add(leftLines.get(0) + " " + operator);
        out.addAll(ErlFormat.renderExprLines(right, indent + 1));
        return out;
      }
    }
    if (left instanceof ErlCallLocal callLocal) {
      List<String> leftLines = new ArrayList<>(callLocal.lines(indent));
      String last = leftLines.remove(leftLines.size() - 1);
      leftLines.add(last + " " + operator + " " + right.asString());
      return leftLines;
    }
    return List.of(ErlFormat.prefixed(indent, inline));
  }
}
