package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExWithClause {
  private final ExPattern pattern;
  private final ExExpr expr;

  public ExWithClause(ExPattern pattern, ExExpr expr) {
    this.pattern = pattern;
    this.expr = expr;
  }

  public static ExWithClause clause(ExPattern pattern, ExExpr expr) {
    return new ExWithClause(pattern, expr);
  }

  public ExPattern pattern() {
    return pattern;
  }

  public ExExpr expr() {
    return expr;
  }

  String headLine(int indent, boolean first) {
    String prefix = first ? "with " : "";
    return IrObject.indent(indent) + prefix + pattern.asString() + " <- " + expr.asString() + ",";
  }
}
