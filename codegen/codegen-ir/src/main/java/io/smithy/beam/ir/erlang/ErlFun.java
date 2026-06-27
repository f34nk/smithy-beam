package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlFun implements ErlExpr {
  private final List<ErlClause> clauses;
  private final boolean compact;

  public ErlFun(List<ErlClause> clauses) {
    this(clauses, false);
  }

  private ErlFun(List<ErlClause> clauses, boolean compact) {
    this.clauses = List.copyOf(clauses);
    this.compact = compact;
  }

  public static ErlFun fun(ErlClause... clauses) {
    return new ErlFun(List.of(clauses));
  }

  public static ErlFun compactFun(ErlClause clause) {
    return new ErlFun(List.of(clause), true);
  }

  public List<ErlClause> clauses() {
    return clauses;
  }

  @Override
  public List<String> lines(int indent) {
    if (compact && clauses.size() == 1) {
      ErlClause clause = clauses.get(0);
      return List.of(
          ErlFormat.prefixed(indent, "fun")
              + funClauseHead(clause)
              + " -> "
              + clause.body().get(0).asString()
              + " end");
    }
    List<String> out = new ArrayList<>();
    if (clauses.size() == 1 && clauses.get(0).patterns().isEmpty()) {
      out.add(ErlFormat.prefixed(indent, "fun() ->"));
      ErlExpr body = clauses.get(0).body().get(0);
      if (body.lines().size() == 1) {
        out.add(ErlFormat.prefixed(indent + 1, body.asString()));
      } else {
        out.addAll(body.lines(indent + 1));
      }
      out.add(ErlFormat.prefixed(indent, "end"));
      return out;
    }
    out.add(ErlFormat.prefixed(indent, "fun"));
    if (clauses.size() > 1) {
      for (int i = 0; i < clauses.size(); i++) {
        out.addAll(funClauseLines(clauses.get(i), indent + 1, i < clauses.size() - 1));
      }
    } else {
      ErlClause clause = clauses.get(0);
      out.set(0, ErlFormat.prefixed(indent, "fun" + funClauseHead(clause) + " ->"));
      out.addAll(ErlFormat.renderExprLines(clause.body().get(0), indent + 1));
    }
    out.add(ErlFormat.prefixed(indent, "end"));
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  private boolean hasMultilineBody() {
    for (ErlClause clause : clauses) {
      if (clause.body().size() != 1 || clause.body().get(0).lines().size() > 1) {
        return true;
      }
    }
    return false;
  }

  List<String> inlineClauseLines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, "fun"));
    for (int i = 0; i < clauses.size(); i++) {
      ErlClause clause = clauses.get(i);
      boolean semicolon = i < clauses.size() - 1;
      out.add(
          ErlFormat.prefixed(indent + 1, funClauseHead(clause))
              + " -> "
              + clause.body().get(0).asString()
              + (semicolon ? ";" : ""));
    }
    out.add(ErlFormat.prefixed(indent, "end"));
    return out;
  }

  private static List<String> funClauseLines(ErlClause clause, int indent, boolean semicolon) {
    List<String> out = new ArrayList<>();
    String head = funClauseHead(clause);
    out.add(ErlFormat.prefixed(indent, head + " ->"));
    ErlFormat.appendClauseBodyLines(out, clause, indent, semicolon, false);
    return out;
  }

  private static String funClauseHead(ErlClause clause) {
    StringBuilder sb = new StringBuilder("(");
    List<ErlPattern> patterns = clause.patterns();
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(patterns.get(i).asString());
    }
    sb.append(')');
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
