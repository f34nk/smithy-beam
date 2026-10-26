package io.beam.lang.gleam;

import java.util.List;

public record ConstructorPattern(String name, List<PatternField> fields) implements Pattern {

  public static ConstructorPattern of(String name) {
    return new ConstructorPattern(name, List.of());
  }

  public static ConstructorPattern of(String name, List<PatternField> fields) {
    return new ConstructorPattern(name, List.copyOf(fields));
  }
}
