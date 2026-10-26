package io.beam.lang.gleam;

import java.util.List;

/** A constructor variant inside a {@link CustomType}. */
public record Variant(String name, List<Field> fields) {

  public static Variant of(String name) {
    return new Variant(name, List.of());
  }

  public static Variant of(String name, List<Field> fields) {
    return new Variant(name, List.copyOf(fields));
  }
}
