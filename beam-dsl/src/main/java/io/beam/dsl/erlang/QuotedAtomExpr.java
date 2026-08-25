package io.beam.dsl.erlang;

/**
 * Quoted atom literal. {@code value} is raw content; the renderer escapes for the target language.
 */
public record QuotedAtomExpr(String value) implements Expression {

  public static QuotedAtomExpr of(String value) {
    return new QuotedAtomExpr(value);
  }
}
