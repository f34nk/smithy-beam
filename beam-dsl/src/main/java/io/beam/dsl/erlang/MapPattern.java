package io.beam.dsl.erlang;

import java.util.List;

public record MapPattern(String variable, List<MapPatternEntry> entries) implements Pattern {

  public static MapPattern bind(String variable) {
    return bind(variable, List.of());
  }

  public static MapPattern bind(String variable, List<MapPatternEntry> entries) {
    return new MapPattern(variable, entries);
  }

  public static MapPattern of(List<MapPatternEntry> entries) {
    return new MapPattern(null, entries);
  }
}
