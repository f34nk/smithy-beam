package io.beam.dsl.elixir;

public record Spec(String text) {

  public static Spec of(String text) {
    return new Spec(text);
  }
}
