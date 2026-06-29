package io.smithy.beam.ir.elixir;

import java.util.List;

/** Renders a blank line inside a function body. */
public final class ExBlankBodyLine implements ExExpr {
  public static ExBlankBodyLine blankLine() {
    return new ExBlankBodyLine();
  }

  @Override
  public List<String> lines(int indent) {
    return List.of("");
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
