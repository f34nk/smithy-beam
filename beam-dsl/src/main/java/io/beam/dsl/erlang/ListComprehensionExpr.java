package io.beam.dsl.erlang;

import java.util.ArrayList;
import java.util.List;

public record ListComprehensionExpr(
    Expression expression, List<ListComprehensionQualifier> qualifiers) implements Expression {

  public static ListComprehensionExpr of(
      Expression expression, List<ListComprehensionQualifier> qualifiers) {
    return new ListComprehensionExpr(expression, qualifiers);
  }

  public static ListComprehensionExpr of(
      Expression expression, Pattern pattern, Expression source, List<Expression> filters) {
    List<ListComprehensionQualifier> qualifiers = new ArrayList<>();
    qualifiers.add(ListComprehensionGenerator.of(pattern, source));
    for (Expression filter : filters) {
      qualifiers.add(ListComprehensionFilter.of(filter));
    }
    return new ListComprehensionExpr(expression, qualifiers);
  }

  public static ListComprehensionExpr of(
      Expression expression, Pattern pattern, Expression source, Expression filter) {
    return of(expression, pattern, source, List.of(filter));
  }

  public static ListComprehensionExpr of(
      Expression expression, Pattern pattern, Expression source) {
    return of(expression, pattern, source, List.of());
  }
}
