package io.beam.dsl.erlang;

import java.util.List;

public record RecordDef(String name, List<TypedField> fields) {

  public static RecordDef of(String name, List<TypedField> fields) {
    return new RecordDef(name, fields);
  }
}
