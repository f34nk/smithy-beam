package io.beam.dsl.elixir;

public record Alias(String module, String asOrNull) {

  public static Alias of(String module) {
    return new Alias(module, null);
  }

  public static Alias of(String module, String as) {
    return new Alias(module, as);
  }
}
