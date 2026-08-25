package io.beam.dsl.elixir;

import java.util.List;

public record StructPattern(String moduleNameOrNull, List<StructPatternField> fields)
    implements Pattern {

  public static StructPattern of(String moduleName, List<StructPatternField> fields) {
    return new StructPattern(moduleName, fields);
  }
}
