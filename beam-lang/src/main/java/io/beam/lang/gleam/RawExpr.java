package io.beam.lang.gleam;

/** Opaque source fragment rendered verbatim (escape hatch for stubs and transitional emission). */
public record RawExpr(String source) implements Expression {

  public static RawExpr of(String source) {
    return new RawExpr(source);
  }
}
