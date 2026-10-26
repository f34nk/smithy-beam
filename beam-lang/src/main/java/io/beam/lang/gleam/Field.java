package io.beam.lang.gleam;

/**
 * A custom-type variant field. When {@code labelOrNull} is null, the field is positional ({@code
 * Unknown(String)}). When set, it is labelled ({@code name: String}).
 */
public record Field(String labelOrNull, String typeExpression) {

  public static Field positional(String typeExpression) {
    return new Field(null, typeExpression);
  }

  public static Field labelled(String label, String typeExpression) {
    return new Field(label, typeExpression);
  }
}
