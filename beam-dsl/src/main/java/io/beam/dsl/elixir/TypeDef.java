package io.beam.dsl.elixir;

public record TypeDef(String name, String body) {

  public static TypeDef of(String name, String body) {
    return new TypeDef(name, body);
  }
}
