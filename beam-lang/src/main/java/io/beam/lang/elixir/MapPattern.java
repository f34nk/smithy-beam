package io.beam.lang.elixir;

import java.util.List;

public record MapPattern(List<MapPatternEntry> entries) implements Pattern {

  public static MapPattern of(List<MapPatternEntry> entries) {
    return new MapPattern(entries);
  }
}
