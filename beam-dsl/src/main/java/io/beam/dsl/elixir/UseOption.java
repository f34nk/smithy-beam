package io.beam.dsl.elixir;

public record UseOption(String key, Expression value) {

  public static UseOption of(String key, Expression value) {
    return new UseOption(key, value);
  }
}
