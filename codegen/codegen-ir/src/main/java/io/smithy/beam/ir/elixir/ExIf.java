package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExIf implements ExExpr {
  private final ExExpr condition;
  private final ExExpr doExpr;
  private final ExExpr elseExprOrNull;
  private final boolean blockForm;

  public ExIf(ExExpr condition, ExExpr doExpr, ExExpr elseExprOrNull, boolean blockForm) {
    this.condition = condition;
    this.doExpr = doExpr;
    this.elseExprOrNull = elseExprOrNull;
    this.blockForm = blockForm;
  }

  public static ExIf ifExpr(ExExpr condition, ExExpr doExpr, ExExpr elseExpr) {
    return new ExIf(condition, doExpr, elseExpr, false);
  }

  public static ExIf ifBlock(ExExpr condition, ExExpr doExpr, ExExpr elseExprOrNull) {
    return new ExIf(condition, doExpr, elseExprOrNull, true);
  }

  public ExExpr condition() {
    return condition;
  }

  public ExExpr doExpr() {
    return doExpr;
  }

  public ExExpr elseExprOrNull() {
    return elseExprOrNull;
  }

  @Override
  public List<String> lines(int indent) {
    if (blockForm) {
      List<String> out = new ArrayList<>();
      out.add(IrObject.indent(indent) + "if " + condition.asString() + " do");
      appendBody(out, doExpr, indent + 1);
      if (elseExprOrNull != null) {
        out.add(IrObject.indent(indent) + "else");
        appendBody(out, elseExprOrNull, indent + 1);
      }
      out.add(IrObject.indent(indent) + "end");
      return out;
    }
    StringBuilder sb = new StringBuilder("if(");
    sb.append(condition.asString()).append(", do: ").append(doExpr.asString());
    if (elseExprOrNull != null) {
      sb.append(", else: ").append(elseExprOrNull.asString());
    }
    sb.append(')');
    return List.of(IrObject.indent(indent) + sb);
  }

  private static void appendBody(List<String> out, ExExpr expr, int indent) {
    if (expr.lines().size() == 1) {
      out.add(IrObject.indent(indent) + expr.asString());
    } else {
      out.addAll(expr.lines(indent));
    }
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
