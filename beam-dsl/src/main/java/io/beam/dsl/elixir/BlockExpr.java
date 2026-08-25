package io.beam.dsl.elixir;

import java.util.List;

public record BlockExpr(List<Expression> statements) implements Expression {

  public static BlockExpr of(List<Expression> statements) {
    return new BlockExpr(statements);
  }
}
