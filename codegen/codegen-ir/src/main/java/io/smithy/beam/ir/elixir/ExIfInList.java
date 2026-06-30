package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExIfInList implements ExExpr {
  static final int SINGLE_LINE_LIMIT = 72;

  private final ExExpr condition;
  private final ExExpr doExpr;
  private final boolean forceSingleLine;

  public ExIfInList(ExExpr condition, ExExpr doExpr, boolean forceSingleLine) {
    this.condition = condition;
    this.doExpr = doExpr;
    this.forceSingleLine = forceSingleLine;
  }

  public static ExIfInList ifInList(ExExpr condition, ExExpr doExpr) {
    return new ExIfInList(condition, doExpr, false);
  }

  public static ExIfInList ifInList(ExExpr condition, ExExpr doExpr, boolean forceSingleLine) {
    return new ExIfInList(condition, doExpr, forceSingleLine);
  }

  @Override
  public List<String> lines(int indent) {
    String singleLine =
        "if(" + condition.asString() + ", do: " + doExpr.asString() + ", else: nil)";
    if (forceSingleLine || singleLine.length() <= SINGLE_LINE_LIMIT) {
      return List.of(IrObject.indent(indent) + singleLine);
    }
    return List.of(
        IrObject.indent(indent) + "if(" + condition.asString() + ",",
        IrObject.indent(indent + 1) + "do: " + doExpr.asString() + ",",
        IrObject.indent(indent + 1) + "else: nil",
        IrObject.indent(indent) + ")");
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
