package io.beam.lang.erlang;

public record ListComprehensionFilter(Expression expression) implements ListComprehensionQualifier {

  public static ListComprehensionFilter of(Expression expression) {
    return new ListComprehensionFilter(expression);
  }
}
