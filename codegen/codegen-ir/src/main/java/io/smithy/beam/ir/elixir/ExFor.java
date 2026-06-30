package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExFor implements ExExpr {
  private final ExExpr expression;
  private final ExPattern generatorPattern;
  private final ExExpr generatorExpr;
  private final List<ExForFilter> filters;
  private final ExExpr intoExprOrNull;

  public ExFor(
      ExExpr expression,
      ExPattern generatorPattern,
      ExExpr generatorExpr,
      List<ExForFilter> filters,
      ExExpr intoExprOrNull) {
    this.expression = expression;
    this.generatorPattern = generatorPattern;
    this.generatorExpr = generatorExpr;
    this.filters = List.copyOf(filters);
    this.intoExprOrNull = intoExprOrNull;
  }

  public ExFor(
      ExExpr expression,
      ExPattern generatorPattern,
      ExExpr generatorExpr,
      List<ExForFilter> filters) {
    this(expression, generatorPattern, generatorExpr, filters, null);
  }

  public static ExFor forExpr(ExExpr expression, ExPattern generatorPattern, ExExpr generatorExpr) {
    return new ExFor(expression, generatorPattern, generatorExpr, List.of());
  }

  public static ExFor forExpr(
      ExExpr expression, ExPattern generatorPattern, ExExpr generatorExpr, ExForFilter... filters) {
    return new ExFor(expression, generatorPattern, generatorExpr, List.of(filters));
  }

  public static ExFor forIntoExpr(
      ExExpr expression,
      ExPattern generatorPattern,
      ExExpr generatorExpr,
      ExExpr intoExpr,
      ExForFilter... filters) {
    return new ExFor(expression, generatorPattern, generatorExpr, List.of(filters), intoExpr);
  }

  public ExExpr expression() {
    return expression;
  }

  public ExPattern generatorPattern() {
    return generatorPattern;
  }

  public ExExpr generatorExpr() {
    return generatorExpr;
  }

  public List<ExForFilter> filters() {
    return filters;
  }

  public ExExpr intoExprOrNull() {
    return intoExprOrNull;
  }

  @Override
  public List<String> lines(int indent) {
    if (expression.lines().size() == 1
        && filters.size() <= 1
        && intoExprOrNull == null
        && generatorExpr.lines().size() == 1) {
      StringBuilder sb = new StringBuilder("for ");
      sb.append(generatorPattern.asString());
      sb.append(" <- ");
      sb.append(generatorExpr.asString());
      for (ExForFilter filter : filters) {
        sb.append(", ");
        sb.append(filter.filter().asString());
      }
      sb.append(" do ");
      sb.append(expression.asString());
      sb.append(" end");
      return List.of(IrObject.indent(indent) + sb);
    }

    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "for " + generatorPattern.asString() + " <-");
    if (generatorExpr.lines().size() == 1) {
      String suffix = (filters.isEmpty() && intoExprOrNull == null) ? " do" : ",";
      out.add(IrObject.indent(indent + 1) + generatorExpr.asString() + suffix);
    } else {
      out.addAll(generatorExpr.lines(indent + 1));
      String last = out.get(out.size() - 1);
      String suffix = (filters.isEmpty() && intoExprOrNull == null) ? " do" : ",";
      out.set(out.size() - 1, last + suffix);
    }
    for (int i = 0; i < filters.size(); i++) {
      ExForFilter filter = filters.get(i);
      boolean lastFilter = i == filters.size() - 1;
      String suffix = (lastFilter && intoExprOrNull == null) ? " do" : ",";
      out.add(IrObject.indent(indent + 1) + filter.filter().asString() + suffix);
    }
    if (intoExprOrNull != null) {
      out.add(IrObject.indent(indent + 1) + "into: " + intoExprOrNull.asString() + " do");
    }
    if (expression.lines().size() == 1) {
      out.add(IrObject.indent(indent + 1) + expression.asString());
    } else {
      out.addAll(expression.lines(indent + 1));
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
