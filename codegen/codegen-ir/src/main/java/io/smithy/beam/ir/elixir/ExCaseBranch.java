package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExCaseBranch {
  private final ExPattern pattern;
  private final List<ExGuard> guards;
  private final ExExpr body;

  public ExCaseBranch(ExPattern pattern, List<ExGuard> guards, ExExpr body) {
    this.pattern = pattern;
    this.guards = List.copyOf(guards);
    this.body = body;
  }

  public static ExCaseBranch branch(ExPattern pattern, ExExpr body) {
    return new ExCaseBranch(pattern, List.of(), body);
  }

  public static ExCaseBranch branch(ExPattern pattern, List<ExGuard> guards, ExExpr body) {
    return new ExCaseBranch(pattern, guards, body);
  }

  public ExPattern pattern() {
    return pattern;
  }

  public List<ExGuard> guards() {
    return guards;
  }

  public ExExpr body() {
    return body;
  }

  List<String> branchLines(int indent, boolean blankBefore) {
    List<String> out = new ArrayList<>();
    if (blankBefore) {
      out.add("");
    }
    if (body.lines().size() == 1) {
      out.add(IrObject.indent(indent) + headText() + " -> " + body.asString());
      return out;
    }
    out.add(IrObject.indent(indent) + headText() + " ->");
    out.addAll(body.lines(indent + 1));
    return out;
  }

  private String headText() {
    StringBuilder sb = new StringBuilder(pattern.asString());
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
