package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExMacroCall implements ExExpr {
  private final String macro;
  private final ExExpr expr;

  private ExMacroCall(String macro, ExExpr expr) {
    this.macro = macro;
    this.expr = expr;
  }

  public static ExMacroCall assertExpr(ExExpr expr) {
    return new ExMacroCall("assert", expr);
  }

  public String macro() {
    return macro;
  }

  public ExExpr expr() {
    return expr;
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + macro + " " + expr.asString());
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
