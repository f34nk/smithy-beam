package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExWith implements ExExpr {
  private final List<ExWithClause> clauses;
  private final List<ExExpr> body;

  public ExWith(List<ExWithClause> clauses, List<ExExpr> body) {
    this.clauses = List.copyOf(clauses);
    this.body = List.copyOf(body);
  }

  public static ExWith withExpr(List<ExWithClause> clauses, ExExpr... body) {
    return new ExWith(clauses, List.of(body));
  }

  public List<ExWithClause> clauses() {
    return clauses;
  }

  public List<ExExpr> body() {
    return body;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < clauses.size(); i++) {
      ExWithClause clause = clauses.get(i);
      String line = clause.headLine(indent, i == 0);
      if (i == clauses.size() - 1) {
        line = line.substring(0, line.length() - 1) + " do";
      }
      out.add(line);
    }
    for (ExExpr expr : body) {
      if (expr.lines().size() == 1) {
        out.add(IrObject.indent(indent + 1) + expr.asString());
      } else {
        out.addAll(expr.lines(indent + 1));
      }
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
