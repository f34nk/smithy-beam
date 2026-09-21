package io.beam.lang.erlang;

import java.util.List;

public record MapEntriesExpr(List<MapEntry> entries) implements Expression {

  public static MapEntriesExpr of(List<MapEntry> entries) {
    return new MapEntriesExpr(entries);
  }
}
