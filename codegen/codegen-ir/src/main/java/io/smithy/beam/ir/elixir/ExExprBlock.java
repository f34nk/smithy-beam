package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExExprBlock implements ExExpr {
  private final List<ExExpr> expressions;

  public ExExprBlock(List<ExExpr> expressions) {
    this.expressions = List.copyOf(expressions);
  }

  public static ExExprBlock block(ExExpr... expressions) {
    return new ExExprBlock(List.of(expressions));
  }

  public List<ExExpr> expressions() {
    return expressions;
  }

  @Override
  public List<String> lines(int indent) {
    if (expressions.size() == 1) {
      return expressions.get(0).lines(indent);
    }
    List<String> out = new ArrayList<>();
    for (ExExpr expr : expressions) {
      out.addAll(expr.lines(indent));
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
