package io.beam.lang.gleam;

/** A Gleam function parameter ({@code name: Type}). Type may be null for anonymous functions. */
public record Param(String name, String typeExpression) {

  public static Param of(String name, String typeExpression) {
    return new Param(name, typeExpression);
  }

  public static Param of(String name) {
    return new Param(name, null);
  }
}
