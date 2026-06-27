package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExMatch implements ExExpr {
  private final ExPattern pattern;
  private final ExExpr expr;

  public ExMatch(ExPattern pattern, ExExpr expr) {
    this.pattern = pattern;
    this.expr = expr;
  }

  public static ExMatch match(ExPattern pattern, ExExpr expr) {
    return new ExMatch(pattern, expr);
  }

  public ExPattern pattern() {
    return pattern;
  }

  public ExExpr expr() {
    return expr;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (expr instanceof ExCase exCase) {
      return exCase.matchLines(pattern, indent);
    }
    if (expr.lines().size() == 1) {
      return List.of(IrObject.indent(indent) + pattern.asString() + " = " + expr.asString());
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + pattern.asString() + " =");
    out.addAll(expr.lines(indent + 1));
    return out;
  }
}
