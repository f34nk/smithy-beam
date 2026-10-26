package io.beam.lang.gleam;

/** A Gleam function parameter ({@code name: Type}). */
public record Param(String name, String typeExpression) {

  public static Param of(String name, String typeExpression) {
    return new Param(name, typeExpression);
  }
}
