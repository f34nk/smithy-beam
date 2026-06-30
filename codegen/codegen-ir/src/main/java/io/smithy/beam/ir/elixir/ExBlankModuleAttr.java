package io.smithy.beam.ir.elixir;

import java.util.List;

/** Renders a blank line among module attributes. */
public final class ExBlankModuleAttr implements ExModuleAttribute {
  public static ExBlankModuleAttr blankLine() {
    return new ExBlankModuleAttr();
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
