package io.beam.dsl.erlang;

import java.util.List;

public record BlockExpr(
    List<Expression> expressions, BlockSeparator separator, boolean terminateWithPeriod)
    implements Expression {

  public enum BlockSeparator {
    COMMA,
    NEWLINE
  }

  public static BlockExpr newlineSeparated(List<Expression> expressions) {
    return new BlockExpr(expressions, BlockSeparator.NEWLINE, false);
  }

  public static BlockExpr newlineSeparated(
      List<Expression> expressions, boolean terminateWithPeriod) {
    return new BlockExpr(expressions, BlockSeparator.NEWLINE, terminateWithPeriod);
  }

  public static BlockExpr commaSeparated(
      List<Expression> expressions, boolean terminateWithPeriod) {
    return new BlockExpr(expressions, BlockSeparator.COMMA, terminateWithPeriod);
  }
}
