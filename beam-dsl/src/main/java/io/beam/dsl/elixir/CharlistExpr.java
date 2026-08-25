package io.beam.dsl.elixir;

/** Charlist literal. {@code value} is raw content; the renderer escapes for the target language. */
public record CharlistExpr(String value) implements Expression {

  public static CharlistExpr of(String value) {
    return new CharlistExpr(value);
  }
}
