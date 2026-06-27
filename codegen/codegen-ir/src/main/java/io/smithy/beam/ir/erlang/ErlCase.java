package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlCase implements ErlExpr {
  private final ErlExpr scrutinee;
  private final List<ErlClause> clauses;

  public ErlCase(ErlExpr scrutinee, List<ErlClause> clauses) {
    this.scrutinee = scrutinee;
    this.clauses = List.copyOf(clauses);
  }

  public static ErlCase caseExpr(ErlExpr scrutinee, ErlClause... clauses) {
    return new ErlCase(scrutinee, List.of(clauses));
  }

  public ErlExpr scrutinee() {
    return scrutinee;
  }

  public List<ErlClause> clauses() {
    return clauses;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    if (ErlFormat.splitCaseScrutinee(indent, scrutinee)) {
      out.add(ErlFormat.prefixed(indent, "case"));
      ErlFormat.appendScrutineeLines(out, indent + 1, scrutinee);
      out.add(ErlFormat.prefixed(indent, "of"));
    } else {
      out.add(ErlFormat.prefixed(indent, "case " + scrutinee.asString() + " of"));
    }
    appendClauseLines(out, indent);
    out.add(ErlFormat.prefixed(indent, "end"));
    return out;
  }

  List<String> matchLines(ErlPattern pattern, int indent) {
    List<String> out = new ArrayList<>();
    if (ErlFormat.splitMatchCase(indent, scrutinee, clauses)) {
      out.add(ErlFormat.prefixed(indent, pattern.asString() + " ="));
      if (ErlFormat.splitCaseScrutinee(indent + 1, scrutinee)) {
        out.add(ErlFormat.prefixed(indent + 1, "case"));
        ErlFormat.appendScrutineeLines(out, indent + 2, scrutinee);
        out.add(ErlFormat.prefixed(indent + 1, "of"));
      } else {
        out.add(ErlFormat.prefixed(indent + 1, "case " + scrutinee.asString() + " of"));
      }
      appendClauseLines(out, indent + 1);
      out.add(ErlFormat.prefixed(indent + 1, "end"));
    } else {
      out.add(
          ErlFormat.prefixed(
              indent, pattern.asString() + " = case " + scrutinee.asString() + " of"));
      appendClauseLines(out, indent);
      out.add(ErlFormat.prefixed(indent, "end"));
    }
    return out;
  }

  private void appendClauseLines(List<String> out, int indent) {
    for (int i = 0; i < clauses.size(); i++) {
      out.addAll(caseClauseLines(clauses.get(i), indent + 1, i < clauses.size() - 1, clauses));
    }
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  private static List<String> caseClauseLines(
      ErlClause clause, int indent, boolean semicolon, List<ErlClause> allClauses) {
    List<String> out = new ArrayList<>();
    String head = clauseHead(clause);
    if (ErlFormat.useInlineCaseClauseBody(clause, indent, head, allClauses)) {
      out.add(
          ErlFormat.prefixed(indent, head + " -> " + clause.body().get(0).asString())
              + (semicolon ? ";" : ""));
    } else {
      out.add(ErlFormat.prefixed(indent, head + " ->"));
      ErlFormat.appendClauseBodyLines(out, clause, indent, semicolon, false);
    }
    return out;
  }

  static String clauseHead(ErlClause clause) {
    StringBuilder sb = new StringBuilder();
    List<ErlPattern> patterns = clause.patterns();
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(patterns.get(i).asString());
    }
    List<ErlGuard> guards = clause.guards();
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
