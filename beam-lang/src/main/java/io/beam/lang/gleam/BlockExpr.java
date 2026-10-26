package io.beam.lang.gleam;

import java.util.List;

/** Sequence of statements / expressions rendered as a Gleam block body. */
public record BlockExpr(List<Expression> statements) implements Expression {

  public static BlockExpr of(List<Expression> statements) {
    return new BlockExpr(List.copyOf(statements));
  }
}
