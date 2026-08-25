package io.beam.dsl.erlang;

import java.util.List;

public record ApplyExpr(Expression callee, List<Expression> arguments) implements Expression {

  public static ApplyExpr of(Expression callee, List<Expression> arguments) {
    return new ApplyExpr(callee, arguments);
  }
}
