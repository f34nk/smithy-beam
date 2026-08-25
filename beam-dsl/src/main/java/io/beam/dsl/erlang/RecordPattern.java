package io.beam.dsl.erlang;

import java.util.List;

public record RecordPattern(String name, String alias, List<RecordPatternField> fields)
    implements Pattern {

  public static RecordPattern of(String name, List<RecordPatternField> fields) {
    return new RecordPattern(name, null, fields);
  }

  public static RecordPattern bind(String alias, String name, List<RecordPatternField> fields) {
    return new RecordPattern(name, alias, fields);
  }
}
