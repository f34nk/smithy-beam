package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExBlankLine implements ExModuleEntry {
  @Override
  public List<String> lines(int indent) {
    return List.of("");
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
