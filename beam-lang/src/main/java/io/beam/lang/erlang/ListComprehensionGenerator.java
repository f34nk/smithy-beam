package io.beam.lang.erlang;

public record ListComprehensionGenerator(Pattern pattern, Expression source)
    implements ListComprehensionQualifier {

  public static ListComprehensionGenerator of(Pattern pattern, Expression source) {
    return new ListComprehensionGenerator(pattern, source);
  }
}
