package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExAnonymousFn implements ExExpr {
  private final List<ExClause> clauses;
  private final boolean compact;

  public ExAnonymousFn(List<ExClause> clauses, boolean compact) {
    this.clauses = List.copyOf(clauses);
    this.compact = compact;
  }

  public ExAnonymousFn(List<ExClause> clauses) {
    this(clauses, false);
  }

  public static ExAnonymousFn fn(ExClause... clauses) {
    return new ExAnonymousFn(List.of(clauses));
  }

  public static ExAnonymousFn compactFn(ExClause clause) {
    return new ExAnonymousFn(List.of(clause), true);
  }

  public List<ExClause> clauses() {
    return clauses;
  }

  @Override
  public List<String> lines(int indent) {
    if (compact && clauses.size() == 1) {
      ExClause clause = clauses.get(0);
      return List.of(
          IrObject.indent(indent)
              + "fn "
              + fnClauseHead(clause)
              + " -> "
              + clause.body().get(0).asString()
              + " end");
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "fn");
    if (clauses.size() == 1 && clauses.get(0).patterns().isEmpty()) {
      out.set(0, IrObject.indent(indent) + "fn ->");
      appendBody(out, clauses.get(0).body().get(0), indent + 1);
      out.add(IrObject.indent(indent) + "end");
      return out;
    }
    if (clauses.size() > 1 || hasMultilineBody()) {
      for (int i = 0; i < clauses.size(); i++) {
        out.addAll(fnClauseLines(clauses.get(i), indent + 1, i < clauses.size() - 1));
      }
    } else {
      ExClause clause = clauses.get(0);
      out.set(0, IrObject.indent(indent) + "fn " + fnClauseHead(clause) + " ->");
      appendBody(out, clause.body().get(0), indent + 1);
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  List<String> inlineClauseLines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "fn");
    for (int i = 0; i < clauses.size(); i++) {
      ExClause clause = clauses.get(i);
      boolean semicolon = i < clauses.size() - 1;
      out.add(
          IrObject.indent(indent + 1)
              + fnClauseHead(clause)
              + " -> "
              + clause.body().get(0).asString()
              + (semicolon ? ";" : ""));
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  private boolean hasMultilineBody() {
    for (ExClause clause : clauses) {
      if (clause.body().size() != 1 || clause.body().get(0).lines().size() > 1) {
        return true;
      }
    }
    return false;
  }

  private static void appendBody(List<String> out, ExExpr body, int indent) {
    if (body.lines().size() == 1) {
      out.add(IrObject.indent(indent) + body.asString());
    } else {
      out.addAll(body.lines(indent));
    }
  }

  private static List<String> fnClauseLines(ExClause clause, int indent, boolean semicolon) {
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + fnClauseHead(clause) + " ->");
    for (ExExpr expr : clause.body()) {
      appendBody(out, expr, indent + 1);
    }
    String last = out.get(out.size() - 1);
    out.set(out.size() - 1, last + (semicolon ? ";" : ""));
    return out;
  }

  private static String fnClauseHead(ExClause clause) {
    StringBuilder sb = new StringBuilder();
    List<ExPattern> patterns = clause.patterns();
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(patterns.get(i).asString());
    }
    List<ExGuard> guards = clause.guards();
    if (!guards.isEmpty()) {
      sb.append(" when ");
      for (int i = 0; i < guards.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(guards.get(i).asString());
      }
    }
    return sb.toString();
  }
}
