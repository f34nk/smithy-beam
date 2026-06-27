package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlTry implements ErlExpr {
  private final List<ErlExpr> body;
  private final List<ErlCatchClause> catchClauses;
  private final List<ErlExpr> afterOrNull;

  public ErlTry(List<ErlExpr> body, List<ErlCatchClause> catchClauses, List<ErlExpr> afterOrNull) {
    this.body = List.copyOf(body);
    this.catchClauses = List.copyOf(catchClauses);
    this.afterOrNull = afterOrNull == null ? null : List.copyOf(afterOrNull);
  }

  public static ErlTry tryExpr(List<ErlExpr> body, List<ErlCatchClause> catchClauses) {
    return new ErlTry(body, catchClauses, null);
  }

  public List<ErlExpr> body() {
    return body;
  }

  public List<ErlCatchClause> catchClauses() {
    return catchClauses;
  }

  public List<ErlExpr> afterOrNull() {
    return afterOrNull;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, "try"));
    out.addAll(tryBodyLines(body, indent + 1));
    out.add(ErlFormat.prefixed(indent, "catch"));
    for (int i = 0; i < catchClauses.size(); i++) {
      String suffix = (i < catchClauses.size() - 1) ? ";" : "";
      out.add(ErlFormat.prefixed(indent + 1, catchClauses.get(i).asString() + suffix));
    }
    out.add(ErlFormat.prefixed(indent, "end"));
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  private static List<String> tryBodyLines(List<ErlExpr> expressions, int indent) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < expressions.size(); i++) {
      boolean hasComma = i < expressions.size() - 1;
      List<String> exprLines = ErlFormat.renderExprLines(expressions.get(i), indent);
      for (int j = 0; j < exprLines.size(); j++) {
        String line = exprLines.get(j);
        if (j == exprLines.size() - 1 && hasComma) {
          line = line + ",";
        }
        out.add(line);
      }
    }
    return out;
  }
}
