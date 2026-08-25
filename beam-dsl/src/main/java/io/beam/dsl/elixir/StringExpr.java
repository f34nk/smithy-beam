package io.beam.dsl.elixir;

/** String literal. {@code value} is raw content; the renderer escapes for the target language. */
public record StringExpr(String value) implements Expression {

  public static StringExpr of(String value) {
    return new StringExpr(value);
  }
}
