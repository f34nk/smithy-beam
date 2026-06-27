package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlCall implements ErlExpr {
  private final ErlAtom module;
  private final String function;
  private final List<ErlExpr> args;

  public ErlCall(ErlAtom module, String function, List<ErlExpr> args) {
    this.module = module;
    this.function = function;
    this.args = List.copyOf(args);
  }

  public static ErlCall call(String module, String function, ErlExpr... args) {
    return new ErlCall(ErlAtom.atom(module), function, List.of(args));
  }

  public static ErlCall filtermap(ErlFun fun, ErlExpr listArg) {
    return call("lists", "filtermap", fun, listArg);
  }

  public ErlAtom module() {
    return module;
  }

  public String function() {
    return function;
  }

  public List<ErlExpr> args() {
    return args;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (isFiltermap()) {
      List<String> out = new ArrayList<>();
      out.add(ErlFormat.prefixed(indent, "lists:filtermap("));
      List<String> funLines = new ArrayList<>(((ErlFun) args.get(0)).inlineClauseLines(indent + 1));
      String lastFunLine = funLines.remove(funLines.size() - 1);
      out.addAll(funLines);
      out.add(lastFunLine + ",");
      out.add(ErlFormat.prefixed(indent + 1, args.get(1).asString()));
      out.add(ErlFormat.prefixed(indent, ")"));
      return out;
    }
    List<String> mapsFilter = formatMapsFilter(indent);
    if (mapsFilter != null) {
      return mapsFilter;
    }
    return ErlFormat.formatPrefixedCall(indent, module.asString() + ":" + function, args, "");
  }

  private List<String> formatMapsFilter(int indent) {
    if (!"filter".equals(function)
        || !"maps".equals(module.value())
        || args.size() != 2
        || !(args.get(0) instanceof ErlFun fun)
        || fun.clauses().size() != 1) {
      return null;
    }
    ErlClause clause = fun.clauses().get(0);
    String funHead = mapsFilterFunHead(clause);
    String opener = "maps:filter(fun" + funHead + " ->";
    if (indent != 0 || ErlFormat.exceedsLineLimit(indent, opener)) {
      return null;
    }
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, opener));
    out.addAll(ErlFormat.renderExprLines(clause.body().get(0), indent + 1));
    out.add(ErlFormat.prefixed(indent, "end, " + args.get(1).asString() + ")"));
    return out;
  }

  private static String mapsFilterFunHead(ErlClause clause) {
    StringBuilder sb = new StringBuilder(" (");
    List<ErlPattern> patterns = clause.patterns();
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(patterns.get(i).asString());
    }
    sb.append(')');
    return sb.toString();
  }

  private boolean isFiltermap() {
    return "filtermap".equals(function)
        && "lists".equals(module.value())
        && args.size() == 2
        && args.get(0) instanceof ErlFun;
  }
}
