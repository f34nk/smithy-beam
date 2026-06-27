package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlExprBlock implements ErlExpr {
  private final List<ErlExpr> expressions;

  public ErlExprBlock(List<ErlExpr> expressions) {
    this.expressions = List.copyOf(expressions);
  }

  public static ErlExprBlock block(ErlExpr... expressions) {
    return new ErlExprBlock(List.of(expressions));
  }

  public List<ErlExpr> expressions() {
    return expressions;
  }

  @Override
  public List<String> lines(int indent) {
    if (expressions.size() == 1) {
      return expressions.get(0).lines(indent);
    }
    List<String> out = new ArrayList<>();
    for (int i = 0; i < expressions.size(); i++) {
      boolean hasComma = i < expressions.size() - 1;
      ErlExpr expr = expressions.get(i);
      if (expr.lines().size() == 1) {
        out.add(IrObject.indent(indent) + expr.asString() + (hasComma ? "," : ""));
      } else {
        List<String> exprLines = expr.lines(indent);
        for (int j = 0; j < exprLines.size(); j++) {
          String line = exprLines.get(j);
          if (j == exprLines.size() - 1 && hasComma) {
            line = line + ",";
          }
          out.add(line);
        }
      }
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
