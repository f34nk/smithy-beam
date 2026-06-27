package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class ErlFormat {
  static final int LINE_LIMIT = 100;

  private ErlFormat() {}

  static boolean exceedsLineLimit(int indent, String content) {
    return IrObject.indent(indent).length() + content.length() > LINE_LIMIT;
  }

  static String prefixed(int indent, String content) {
    return IrObject.indent(indent) + content;
  }

  static String generatorLinePrefix(int indent) {
    int exprIndentSpaces = IrObject.indent(indent + 1).length();
    int genIndentSpaces = Math.max(0, exprIndentSpaces - 3);
    return " ".repeat(genIndentSpaces) + "|| ";
  }

  static boolean clauseNeedsBlockBody(ErlClause clause) {
    if (clause.forceBlockBody()) {
      return true;
    }
    if (clause.body().size() != 1) {
      return true;
    }
    return clause.body().get(0).lines().size() > 1;
  }

  static boolean anyClauseNeedsBlockBody(List<ErlClause> clauses) {
    for (ErlClause clause : clauses) {
      if (clauseNeedsBlockBody(clause)) {
        return true;
      }
    }
    return false;
  }

  static boolean anyInlineClauseExceedsLimit(
      List<ErlClause> clauses, int indent, Function<ErlClause, String> headFn) {
    for (ErlClause clause : clauses) {
      if (clauseNeedsBlockBody(clause)) {
        return true;
      }
      if (clause.body().size() == 1) {
        ErlExpr body = clause.body().get(0);
        if (functionClauseBodyPrefersBlock(body)) {
          return true;
        }
        if (body.lines().size() == 1) {
          String inline = headFn.apply(clause) + " -> " + body.asString();
          if (exceedsLineLimit(indent, inline)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  static boolean functionClauseBodyPrefersBlock(ErlExpr body) {
    if (body instanceof ErlList list) {
      for (ErlExpr element : list.elements()) {
        if (expressionContainsCallOrOp(element)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean expressionContainsCallOrOp(ErlExpr expr) {
    if (expr instanceof ErlOp || expr instanceof ErlCall || expr instanceof ErlCallLocal) {
      return true;
    }
    if (expr instanceof ErlTuple tuple) {
      for (ErlExpr element : tuple.elements()) {
        if (expressionContainsCallOrOp(element)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean useInlineClauseBody(
      ErlClause clause, int indent, String head, boolean blockAllClauses) {
    if (clauseNeedsBlockBody(clause)) {
      return false;
    }
    if (clause.body().size() != 1) {
      return false;
    }
    if (blockAllClauses && clause.guards().isEmpty()) {
      return false;
    }
    ErlExpr body = clause.body().get(0);
    if (renderExprLines(body, indent + 1).size() > 1) {
      return false;
    }
    String inline = head + " -> " + body.asString();
    return !exceedsLineLimit(indent, inline);
  }

  static boolean useInlineCaseClauseBody(
      ErlClause clause, int indent, String head, List<ErlClause> allClauses) {
    if (clauseNeedsBlockBody(clause)) {
      return false;
    }
    if (clause.body().size() != 1) {
      return false;
    }
    ErlExpr body = clause.body().get(0);
    if (anyClauseHasNestedCase(allClauses) && isPassThroughCaseClause(clause, body)) {
      return false;
    }
    if (anyClauseHasNestedCase(allClauses)) {
      return false;
    }
    if (anyClauseHasMultilineExprBody(allClauses)) {
      return false;
    }
    if (anyClauseNeedsBlockBody(allClauses) && !isSimpleCaseBody(body)) {
      return false;
    }
    if (renderExprLines(body, indent + 1).size() > 1) {
      return false;
    }
    String inline = head + " -> " + body.asString();
    return !exceedsLineLimit(indent, inline);
  }

  static boolean isSimpleCaseBody(ErlExpr body) {
    if (body instanceof ErlVar || body instanceof ErlAtom) {
      return true;
    }
    if (body instanceof ErlCallLocal call) {
      return call.function().length() <= 5;
    }
    return false;
  }

  private static boolean isPassThroughCaseClause(ErlClause clause, ErlExpr body) {
    if (!(body instanceof ErlVar var)) {
      return false;
    }
    if (clause.patterns().size() != 1
        || !(clause.patterns().get(0) instanceof ErlVarPattern pattern)) {
      return false;
    }
    return pattern.name().equals(var.name());
  }

  static boolean splitCaseScrutinee(int indent, ErlExpr scrutinee) {
    if (scrutinee instanceof ErlListComprehension comp
        && comprehensionLines(comp, indent).size() > 1) {
      return true;
    }
    if (scrutinee instanceof ErlList list && list.lines(indent).size() > 1) {
      return true;
    }
    return exceedsLineLimit(indent, "case " + scrutinee.asString() + " of");
  }

  static boolean splitMatchCase(int indent) {
    return indent > 0;
  }

  static boolean splitMatchCase(int indent, ErlExpr scrutinee, List<ErlClause> clauses) {
    if (indent > 0) {
      return true;
    }
    if (scrutinee instanceof ErlCall || scrutinee instanceof ErlCallLocal) {
      return true;
    }
    return clauseBodiesForceMatchSplit(clauses);
  }

  private static boolean clauseBodiesForceMatchSplit(List<ErlClause> clauses) {
    return anyClauseHasNestedCase(clauses);
  }

  private static boolean anyClauseHasNestedCase(List<ErlClause> clauses) {
    for (ErlClause clause : clauses) {
      for (ErlExpr expr : clause.body()) {
        if (containsNestedCase(expr)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean anyClauseHasMultilineExprBody(List<ErlClause> clauses) {
    for (ErlClause clause : clauses) {
      if (clause.body().size() != 1) {
        continue;
      }
      ErlExpr body = clause.body().get(0);
      if (body instanceof ErlExprBlock block && block.expressions().size() > 1) {
        continue;
      }
      if (renderExprLines(body, 1).size() > 1) {
        return true;
      }
      if (nestedTupleBodyPrefersBlock(body)) {
        return true;
      }
    }
    return false;
  }

  private static boolean nestedTupleBodyPrefersBlock(ErlExpr body) {
    if (!(body instanceof ErlTuple tuple)
        || tuple.elements().size() != 2
        || !(tuple.elements().get(1) instanceof ErlTuple nested)) {
      return false;
    }
    for (ErlExpr element : nested.elements()) {
      if (element instanceof ErlCall || element instanceof ErlCallLocal) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsNestedCase(ErlExpr expr) {
    if (expr instanceof ErlCase) {
      return true;
    }
    if (expr instanceof ErlMatch match && match.expr() instanceof ErlCase) {
      return true;
    }
    if (expr instanceof ErlExprBlock block) {
      for (ErlExpr nested : block.expressions()) {
        if (containsNestedCase(nested)) {
          return true;
        }
      }
    }
    return false;
  }

  static List<String> formatPrefixedCall(
      int indent, String prefix, List<ErlExpr> args, String closeSuffix) {
    return formatCall(indent, prefix, "(", args, ")" + closeSuffix);
  }

  static List<String> formatCall(
      int indent, String prefix, String open, List<ErlExpr> args, String close) {
    String inline = inlineCall(prefix, open, args, close);
    if (!needsMultilineCall(indent, inline, args)) {
      return List.of(prefixed(indent, inline));
    }

    if (!args.isEmpty()) {
      ErlExpr lastArg = args.get(args.size() - 1);
      boolean lastIsListOrComp =
          lastArg instanceof ErlList || lastArg instanceof ErlListComprehension;
      StringBuilder allArgs = new StringBuilder();
      for (int i = 0; i < args.size(); i++) {
        if (i > 0) {
          allArgs.append(", ");
        }
        allArgs.append(args.get(i).asString());
      }
      if (!lastIsListOrComp
          && !exceedsLineLimit(indent + 1, allArgs.toString())
          && !argsNeedMultilineRendering(indent, args)) {
        List<String> grouped = new ArrayList<>();
        grouped.add(prefixed(indent, prefix + open));
        grouped.add(prefixed(indent + 1, allArgs.toString()));
        if (close.startsWith(")")) {
          grouped.add(prefixed(indent, close));
        } else {
          grouped.set(grouped.size() - 1, grouped.get(grouped.size() - 1) + close);
        }
        return grouped;
      }
    }

    if (args.size() == 2
        && renderExprLines(args.get(0), indent + 1).size() == 1
        && renderExprLines(args.get(1), indent + 1).size() == 1) {
      String bothArgs = args.get(0).asString() + ", " + args.get(1).asString();
      if (!exceedsLineLimit(indent + 1, bothArgs)) {
        List<String> pair = new ArrayList<>();
        pair.add(prefixed(indent, prefix + open));
        pair.add(prefixed(indent + 1, bothArgs));
        if (close.startsWith(")")) {
          pair.add(prefixed(indent, close));
        } else {
          pair.set(pair.size() - 1, pair.get(pair.size() - 1) + close);
        }
        return pair;
      }
    }

    if (args.size() >= 2 && !leadingArgsNeedMultiline(indent, prefix, open, args)) {
      StringBuilder prefixArgs = new StringBuilder(prefix).append(open);
      for (int i = 0; i < args.size() - 1; i++) {
        if (i > 0) {
          prefixArgs.append(", ");
        }
        prefixArgs.append(args.get(i).asString());
      }
      prefixArgs.append(", ");
      ErlExpr lastArg = args.get(args.size() - 1);
      String opener = prefixArgs.toString();
      if (lastArg instanceof ErlListComprehension comp) {
        return formatCallWithComprehensionArg(indent, opener, close, comp);
      }
      if (lastArg instanceof ErlList list && list.tailOrNull() == null) {
        String inlineList = listInline(list);
        String inlineLast = opener + inlineList + close;
        if (!exceedsLineLimit(indent, inlineLast)) {
          return List.of(prefixed(indent, inlineLast));
        }
        List<String> out = new ArrayList<>();
        out.add(prefixed(indent, opener + "["));
        out.add(prefixed(indent + 1, listElementsInline(list)));
        out.add(prefixed(indent, "]" + close));
        return out;
      }
      if (lastArg.lines().size() == 1) {
        String inlineLast = opener + lastArg.asString() + close;
        if (!exceedsLineLimit(indent, inlineLast)) {
          return List.of(prefixed(indent, inlineLast));
        }
      }
      List<String> out = new ArrayList<>();
      out.add(prefixed(indent, opener));
      if (lastArg.lines().size() == 1) {
        out.add(prefixed(indent + 1, lastArg.asString() + close));
      } else {
        List<String> lastLines = new ArrayList<>(lastArg.lines(indent + 1));
        String last = lastLines.remove(lastLines.size() - 1);
        out.addAll(lastLines);
        out.add(prefixed(indent + 1, last + close));
      }
      return out;
    }

    if (args.size() == 1 && args.get(0) instanceof ErlListComprehension comp) {
      return formatCallWithComprehensionArg(indent, prefix + open, close, comp);
    }

    List<String> out = new ArrayList<>();
    out.add(prefixed(indent, prefix + open));
    for (int i = 0; i < args.size(); i++) {
      boolean trailingComma = i < args.size() - 1;
      ErlExpr arg = args.get(i);
      List<String> argLines = renderExprLines(arg, indent + 1);
      if (argLines.size() == 1) {
        out.add(argLines.get(0) + (trailingComma ? "," : ""));
      } else {
        for (int j = 0; j < argLines.size(); j++) {
          String line = argLines.get(j);
          if (j == argLines.size() - 1 && trailingComma) {
            line = line + ",";
          }
          out.add(line);
        }
      }
    }
    out.add(prefixed(indent, close));
    return out;
  }

  static String inlineCall(String prefix, String open, List<ErlExpr> args, String close) {
    StringBuilder sb = new StringBuilder(prefix).append(open);
    for (int i = 0; i < args.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(args.get(i).asString());
    }
    sb.append(close);
    return sb.toString();
  }

  private static String listElementsInline(ErlList list) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.elements().size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(list.elements().get(i).asString());
    }
    if (list.tailOrNull() != null) {
      if (!list.elements().isEmpty()) {
        sb.append(" | ");
      }
      sb.append(list.tailOrNull().asString());
    }
    return sb.toString();
  }

  private static String listInline(ErlList list) {
    return "[" + listElementsInline(list) + "]";
  }

  private static boolean needsMultilineCall(int indent, String inline, List<ErlExpr> args) {
    if (exceedsLineLimit(indent, inline)) {
      return true;
    }
    for (ErlExpr arg : args) {
      if (arg instanceof ErlListComprehension comp && comprehensionLines(comp, indent).size() > 1) {
        return true;
      }
      if (renderExprLines(arg, indent + 1).size() > 1) {
        return true;
      }
      if (arg instanceof ErlFun fun && (fun.clauses().size() > 1 || funHasMultilineBody(fun))) {
        return true;
      }
    }
    return false;
  }

  private static boolean leadingArgsNeedMultiline(
      int indent, String prefix, String open, List<ErlExpr> args) {
    for (int i = 0; i < args.size() - 1; i++) {
      ErlExpr arg = args.get(i);
      if (renderExprLines(arg, indent + 1).size() > 1) {
        return true;
      }
      if (arg instanceof ErlFun fun && (fun.clauses().size() > 1 || funHasMultilineBody(fun))) {
        return true;
      }
    }
    return false;
  }

  private static boolean funHasMultilineBody(ErlFun fun) {
    for (ErlClause clause : fun.clauses()) {
      if (clause.body().size() != 1 || renderExprLines(clause.body().get(0), 1).size() > 1) {
        return true;
      }
    }
    return false;
  }

  private static boolean argsNeedMultilineRendering(int indent, List<ErlExpr> args) {
    for (ErlExpr arg : args) {
      if (renderExprLines(arg, indent + 1).size() > 1) {
        return true;
      }
      if (arg instanceof ErlFun fun && (fun.clauses().size() > 1 || funHasMultilineBody(fun))) {
        return true;
      }
    }
    return false;
  }

  private static List<String> comprehensionLines(ErlListComprehension comp, int indent) {
    return comp.lines(indent);
  }

  private static List<String> formatCallWithComprehensionArg(
      int indent, String prefixThroughOpen, String close, ErlListComprehension comp) {
    List<String> out = new ArrayList<>();
    out.add(prefixed(indent, prefixThroughOpen + "["));
    List<String> compLines = comp.lines(indent);
    for (int i = 1; i < compLines.size() - 1; i++) {
      out.add(compLines.get(i));
    }
    out.add(prefixed(indent, "]" + close));
    return out;
  }

  static void appendScrutineeLines(List<String> out, int indent, ErlExpr scrutinee) {
    if (scrutinee.lines().size() == 1 && !exceedsLineLimit(indent, scrutinee.asString())) {
      out.add(prefixed(indent, scrutinee.asString()));
      return;
    }
    out.addAll(scrutinee.lines(indent));
  }

  static List<String> appendClauseBodyLines(
      List<String> out, ErlClause clause, int indent, boolean semicolon, boolean period) {
    for (ErlExpr expr : clause.body()) {
      out.addAll(renderExprLines(expr, indent + 1));
    }
    String suffix = period ? "." : (semicolon ? ";" : "");
    String last = out.get(out.size() - 1);
    out.set(out.size() - 1, last + suffix);
    return out;
  }

  static List<String> renderExprLines(ErlExpr expr, int indent) {
    List<String> lines = expr.lines(indent);
    if (lines.size() == 1) {
      String line = lines.get(0);
      if (!line.startsWith(IrObject.indent(indent))) {
        return List.of(prefixed(indent, line));
      }
      return lines;
    }
    List<String> out = new ArrayList<>();
    for (String line : lines) {
      if (!line.startsWith(IrObject.indent(indent))) {
        out.add(prefixed(indent, line));
      } else {
        out.add(line);
      }
    }
    return out;
  }
}
