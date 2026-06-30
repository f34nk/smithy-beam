package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExClause implements IrObject {
  private final List<ExPattern> patterns;
  private final List<ExGuard> guards;
  private final List<ExExpr> body;
  private final boolean inlineDo;
  private final boolean forceBlockBody;

  public ExClause(
      List<ExPattern> patterns,
      List<ExGuard> guards,
      List<ExExpr> body,
      boolean inlineDo,
      boolean forceBlockBody) {
    this.patterns = List.copyOf(patterns);
    this.guards = List.copyOf(guards);
    this.body = List.copyOf(body);
    this.inlineDo = inlineDo;
    this.forceBlockBody = forceBlockBody;
  }

  public ExClause(List<ExPattern> patterns, List<ExGuard> guards, List<ExExpr> body) {
    this(patterns, guards, body, false, false);
  }

  public static ExClause clause(List<ExPattern> patterns, List<ExGuard> guards, ExExpr... body) {
    return new ExClause(patterns, guards, List.of(body));
  }

  public static ExClause clause(List<ExPattern> patterns, ExExpr... body) {
    return clause(patterns, List.of(), body);
  }

  public static ExClause inlineClause(List<ExPattern> patterns, List<ExGuard> guards, ExExpr body) {
    return new ExClause(patterns, guards, List.of(body), true, false);
  }

  public static ExClause inlineClause(List<ExPattern> patterns, ExExpr body) {
    return inlineClause(patterns, List.of(), body);
  }

  public static ExClause blockClause(
      List<ExPattern> patterns, List<ExGuard> guards, ExExpr... body) {
    return new ExClause(patterns, guards, List.of(body), false, true);
  }

  public static ExClause blockClause(List<ExPattern> patterns, ExExpr... body) {
    return blockClause(patterns, List.of(), body);
  }

  public static ExClause blockClauseSingleLineHead(List<ExPattern> patterns, ExExpr... body) {
    return blockClause(patterns, body);
  }

  public List<ExPattern> patterns() {
    return patterns;
  }

  public List<ExGuard> guards() {
    return guards;
  }

  public List<ExExpr> body() {
    return body;
  }

  public boolean inlineDo() {
    return inlineDo;
  }

  public boolean forceBlockBody() {
    return forceBlockBody;
  }

  @Override
  public List<String> lines() {
    throw new UnsupportedOperationException("Use lines(indent, keyword, name, trailingComma)");
  }

  public List<String> lines(int indent, String keyword, String name, boolean trailingComma) {
    if (usesInlineDo()) {
      return List.of(
          IrObject.indent(indent)
              + buildInlineHead(keyword, name)
              + ", do: "
              + body.get(0).asString());
    }

    List<String> out = new ArrayList<>();
    if (shouldBreakStructFunctionHead()) {
      ExStructPattern structPattern = (ExStructPattern) patterns.get(0);
      out.addAll(structPattern.functionHeadLines(indent, keyword, name, whenClauseText(), true));
    } else {
      out.add(IrObject.indent(indent) + buildBlockHead(keyword, name) + " do");
    }

    for (ExExpr expr : body) {
      if (expr instanceof ExBlankBodyLine) {
        out.add("");
        continue;
      }
      if (expr.lines().size() == 1) {
        out.add(IrObject.indent(indent + 1) + expr.asString());
      } else {
        out.addAll(expr.lines(indent + 1));
      }
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  private boolean usesInlineDo() {
    if (forceBlockBody) {
      return false;
    }
    if (inlineDo) {
      return true;
    }
    return body.size() == 1 && body.get(0).lines().size() == 1;
  }

  private boolean shouldBreakStructFunctionHead() {
    return patterns.size() == 1
        && patterns.get(0) instanceof ExStructPattern structPattern
        && structPattern.breaksFunctionHead();
  }

  private String buildInlineHead(String keyword, String name) {
    if (patterns.isEmpty()) {
      return keyword + " " + name + guardSuffix();
    }
    return keyword + " " + name + "(" + patternText() + ")" + guardSuffix();
  }

  private String buildBlockHead(String keyword, String name) {
    if (patterns.isEmpty()) {
      return keyword + " " + name + guardSuffix();
    }
    return keyword + " " + name + "(" + patternText() + ")" + guardSuffix();
  }

  private String patternText() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(patterns.get(i).asString());
    }
    return sb.toString();
  }

  private String guardSuffix() {
    String when = whenClauseText();
    return when == null ? "" : " " + when;
  }

  private String whenClauseText() {
    return ExGuard.whenClause(guards);
  }
}
