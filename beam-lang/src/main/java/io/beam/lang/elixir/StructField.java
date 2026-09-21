package io.beam.lang.elixir;

public record StructField(String name, Expression value) {

  public static StructField of(String name, Expression value) {
    return new StructField(name, value);
  }
}
