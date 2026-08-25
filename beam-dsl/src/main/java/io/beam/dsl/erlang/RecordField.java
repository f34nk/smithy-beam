package io.beam.dsl.erlang;

public record RecordField(String name, Expression value) {

  public static RecordField of(String name, Expression value) {
    return new RecordField(name, value);
  }
}
