package io.beam.dsl.elixir;

import java.util.List;

public record MapExpr(Expression baseOrNull, List<MapEntry> entries) implements Expression {

  public static MapExpr of(List<MapEntry> entries) {
    return new MapExpr(null, entries);
  }

  public static MapExpr of(Expression base, List<MapEntry> entries) {
    return new MapExpr(base, entries);
  }
}
