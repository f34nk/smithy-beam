package io.beam.dsl.erlang;

import java.util.List;

public record TypedField(
    String name, String type, String defaultValueOrNull, List<String> fieldCommentsOrNull) {

  public static TypedField of(String name, String type) {
    return new TypedField(name, type, null, null);
  }

  public static TypedField of(String name, String type, String defaultValue) {
    return new TypedField(name, type, defaultValue, null);
  }

  public static TypedField of(
      String name, String type, String defaultValue, List<String> fieldComments) {
    return new TypedField(name, type, defaultValue, fieldComments);
  }
}
