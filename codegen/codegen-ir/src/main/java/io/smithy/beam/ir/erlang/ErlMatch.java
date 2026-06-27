package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlMatch implements ErlExpr {
  private final ErlPattern pattern;
  private final ErlExpr expr;

  public ErlMatch(ErlPattern pattern, ErlExpr expr) {
    this.pattern = pattern;
    this.expr = expr;
  }

  public static ErlMatch match(ErlPattern pattern, ErlExpr expr) {
    return new ErlMatch(pattern, expr);
  }

  public ErlPattern pattern() {
    return pattern;
  }

  public ErlExpr expr() {
    return expr;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (expr instanceof ErlCase erlCase) {
      return erlCase.matchLines(pattern, indent);
    }
    if (pattern instanceof ErlBinPattern binPattern) {
      List<String> patternLines = binPattern.lines(indent);
      if (patternLines.size() > 1) {
        List<String> out = new ArrayList<>(patternLines.subList(0, patternLines.size() - 1));
        String last = patternLines.get(patternLines.size() - 1);
        if (last.startsWith(IrObject.indent(indent + 1))) {
          last = last.substring(IrObject.indent(indent + 1).length());
        }
        out.add(ErlFormat.prefixed(indent + 1, last + " = " + expr.asString()));
        return out;
      }
    }
    List<String> exprLines = ErlFormat.renderExprLines(expr, indent);
    String assignmentPrefix = pattern.asString() + " = ";
    if (exprLines.size() == 1
        && !ErlFormat.exceedsLineLimit(indent, assignmentPrefix + expr.asString())) {
      return List.of(ErlFormat.prefixed(indent, assignmentPrefix + expr.asString()));
    }
    if (exprLines.size() > 1) {
      String first = exprLines.get(0);
      if (first.startsWith(IrObject.indent(indent))) {
        first = first.substring(IrObject.indent(indent).length());
      }
      if (!ErlFormat.exceedsLineLimit(indent, assignmentPrefix + first)) {
        List<String> out = new ArrayList<>();
        out.add(ErlFormat.prefixed(indent, assignmentPrefix + first));
        out.addAll(exprLines.subList(1, exprLines.size()));
        return out;
      }
    }
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, pattern.asString() + " ="));
    out.addAll(exprLines);
    return out;
  }
}
