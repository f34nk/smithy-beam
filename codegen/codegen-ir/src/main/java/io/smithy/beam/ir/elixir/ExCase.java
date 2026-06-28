package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExCase implements ExExpr {
  private final ExExpr scrutinee;
  private final List<ExCaseBranch> branches;
  private final boolean blankBetweenBranches;

  public ExCase(ExExpr scrutinee, List<ExCaseBranch> branches, boolean blankBetweenBranches) {
    this.scrutinee = scrutinee;
    this.branches = List.copyOf(branches);
    this.blankBetweenBranches = blankBetweenBranches;
  }

  public ExCase(ExExpr scrutinee, List<ExCaseBranch> branches) {
    this(scrutinee, branches, false);
  }

  public static ExCase caseExpr(ExExpr scrutinee, ExCaseBranch... branches) {
    return new ExCase(scrutinee, List.of(branches));
  }

  public static ExCase caseExpr(
      ExExpr scrutinee, List<ExCaseBranch> branches, boolean blankBetweenBranches) {
    return new ExCase(scrutinee, branches, blankBetweenBranches);
  }

  public ExExpr scrutinee() {
    return scrutinee;
  }

  public List<ExCaseBranch> branches() {
    return branches;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "case " + scrutinee.asString() + " do");
    appendBranchLines(out, indent + 1, blankBetweenBranches);
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  List<String> matchLines(ExPattern pattern, int indent) {
    List<String> out = new ArrayList<>();
    out.add(
        IrObject.indent(indent)
            + pattern.asString()
            + " = case "
            + scrutinee.asString()
            + " do");
    appendBranchLines(out, indent + 1, blankBetweenBranches);
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  private void appendBranchLines(List<String> out, int indent, boolean blankBetweenBranches) {
    for (int i = 0; i < branches.size(); i++) {
      out.addAll(branches.get(i).branchLines(indent, blankBetweenBranches && i > 0));
    }
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
