package io.beam.dsl.elixir;

public record MapEntry(Expression key, Expression value, boolean arrowSyntax) {

  public static MapEntry atomKey(String key, Expression value) {
    return new MapEntry(AtomExpr.of(key), value, false);
  }

  public static MapEntry stringKey(String key, Expression value) {
    return new MapEntry(StringExpr.of(key), value, true);
  }

  public static MapEntry pair(Expression key, Expression value) {
    return new MapEntry(key, value, true);
  }
}
