package io.beam.dsl.erlang;

public record RecordPatternField(String name, Pattern pattern) {

  public static RecordPatternField of(String name, Pattern pattern) {
    return new RecordPatternField(name, pattern);
  }
}
