package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlClause implements IrObject {
  private final List<ErlPattern> patterns;
  private final List<ErlGuard> guards;
  private final List<ErlExpr> body;
  private final boolean forceBlockBody;

  public ErlClause(
      List<ErlPattern> patterns,
      List<ErlGuard> guards,
      List<ErlExpr> body,
      boolean forceBlockBody) {
    this.patterns = List.copyOf(patterns);
    this.guards = List.copyOf(guards);
    this.body = List.copyOf(body);
    this.forceBlockBody = forceBlockBody;
  }

  public ErlClause(List<ErlPattern> patterns, List<ErlGuard> guards, List<ErlExpr> body) {
    this(patterns, guards, body, false);
  }

  public static ErlClause clause(
      List<ErlPattern> patterns, List<ErlGuard> guards, ErlExpr... body) {
    return new ErlClause(patterns, guards, List.of(body));
  }

  public static ErlClause clause(List<ErlPattern> patterns, ErlExpr... body) {
    return clause(patterns, List.of(), body);
  }

  public static ErlClause blockClause(
      List<ErlPattern> patterns, List<ErlGuard> guards, ErlExpr... body) {
    return new ErlClause(patterns, guards, List.of(body), true);
  }

  public static ErlClause blockClause(List<ErlPattern> patterns, ErlExpr... body) {
    return blockClause(patterns, List.of(), body);
  }

  public List<ErlPattern> patterns() {
    return patterns;
  }

  public List<ErlGuard> guards() {
    return guards;
  }

  public List<ErlExpr> body() {
    return body;
  }

  public boolean forceBlockBody() {
    return forceBlockBody;
  }

  @Override
  public List<String> lines() {
    throw new UnsupportedOperationException("Use lines(indent, functionName, semicolon)");
  }

  public List<String> lines(int indent, String functionName, boolean semicolon) {
    return lines(indent, functionName, semicolon, false);
  }

  public List<String> lines(int indent, String functionName, boolean semicolon, boolean blockAll) {
    List<String> out = new ArrayList<>();
    List<String> headLines = buildHeadLines(indent, functionName);
    String inlineHead = headLines.size() == 1 ? headLines.get(0) : null;
    for (int i = 0; i < headLines.size(); i++) {
      int lineIndent = headLines.size() > 1 && i == headLines.size() - 2 ? indent + 1 : indent;
      out.add(ErlFormat.prefixed(lineIndent, headLines.get(i).stripLeading()));
    }
    String headForInlineCheck = inlineHead != null ? inlineHead : headLines.get(0);
    if (ErlFormat.useInlineClauseBody(this, indent, headForInlineCheck, blockAll)) {
      out.set(
          out.size() - 1,
          out.get(out.size() - 1) + " -> " + body.get(0).asString() + (semicolon ? ";" : "."));
    } else {
      out.set(out.size() - 1, out.get(out.size() - 1) + " ->");
      ErlFormat.appendClauseBodyLines(out, this, indent, semicolon, !semicolon);
    }
    return out;
  }

  String buildHead(String functionName) {
    List<String> headLines = buildHeadLines(0, functionName);
    if (headLines.size() == 1) {
      return headLines.get(0);
    }
    return String.join("\n", headLines);
  }

  private List<String> buildHeadLines(int indent, String functionName) {
    String inline = inlineHead(functionName);
    if (!ErlFormat.exceedsLineLimit(indent, inline)) {
      return List.of(inline);
    }
    if (patterns.size() == 1 && patterns.get(0) instanceof ErlMatchPattern match) {
      List<String> out = new ArrayList<>();
      out.add(functionName + "(");
      out.add(match.asString());
      out.add(")" + guardSuffix());
      return out;
    }
    if (patterns.size() >= 1
        && patterns.get(patterns.size() - 1) instanceof ErlRecordPattern record) {
      List<String> out = new ArrayList<>();
      StringBuilder prefix = new StringBuilder(functionName).append('(');
      for (int i = 0; i < patterns.size() - 1; i++) {
        if (i > 0) {
          prefix.append(", ");
        }
        prefix.append(patterns.get(i).asString());
      }
      if (patterns.size() > 1) {
        prefix.append(", ");
      }
      if (record.aliasOrNull() != null) {
        prefix.append(record.aliasOrNull()).append(" = ");
      }
      prefix.append('#').append(record.name()).append('{');
      out.add(prefix.toString());
      StringBuilder fieldLine = new StringBuilder();
      for (int i = 0; i < record.fields().size(); i++) {
        if (i > 0) {
          fieldLine.append(", ");
        }
        fieldLine.append(record.fields().get(i).asString());
      }
      out.add(fieldLine.toString());
      out.add("})" + guardSuffix());
      return out;
    }
    return List.of(inline);
  }

  private String inlineHead(String functionName) {
    StringBuilder sb = new StringBuilder(functionName);
    sb.append('(');
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(patterns.get(i).asString());
    }
    sb.append(')');
    if (!guards.isEmpty()) {
      sb.append(" when ").append(guardText());
    }
    return sb.toString();
  }

  private String guardSuffix() {
    return guards.isEmpty() ? "" : " when " + guardText();
  }

  private String guardText() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < guards.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(guards.get(i).asString());
    }
    return sb.toString();
  }
}
