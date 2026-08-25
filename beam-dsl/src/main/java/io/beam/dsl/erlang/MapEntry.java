package io.beam.dsl.erlang;

public record MapEntry(Expression key, Expression value, boolean updateOnly) {

  public static MapEntry of(Expression key, Expression value) {
    return new MapEntry(key, value, false);
  }

  public static MapEntry of(Expression key, Expression value, boolean updateOnly) {
    return new MapEntry(key, value, updateOnly);
  }
}
