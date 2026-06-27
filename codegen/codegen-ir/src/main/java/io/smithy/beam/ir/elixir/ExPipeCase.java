package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExPipeCase implements ExExpr {
  private final ExExpr expr;
  private final List<ExCaseBranch> branches;
  private final boolean blankBetweenBranches;

  public ExPipeCase(ExExpr expr, List<ExCaseBranch> branches, boolean blankBetweenBranches) {
    this.expr = expr;
    this.branches = List.copyOf(branches);
    this.blankBetweenBranches = blankBetweenBranches;
  }

  public static ExPipeCase pipeCase(ExExpr expr, ExCaseBranch... branches) {
    return new ExPipeCase(expr, List.of(branches), false);
  }

  public static ExPipeCase pipeCase(
      ExExpr expr, List<ExCaseBranch> branches, boolean blankBetweenBranches) {
    return new ExPipeCase(expr, branches, blankBetweenBranches);
  }

  public ExExpr expr() {
    return expr;
  }

  public List<ExCaseBranch> branches() {
    return branches;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    if (expr.lines().size() == 1) {
      out.add(IrObject.indent(indent) + expr.asString());
    } else {
      out.addAll(expr.lines(indent));
    }
    out.add(IrObject.indent(indent) + "|> case do");
    for (int i = 0; i < branches.size(); i++) {
      out.addAll(branches.get(i).branchLines(indent + 1, blankBetweenBranches && i > 0));
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
