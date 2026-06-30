package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExSourceLine implements ExModuleEntry {
  private final String line;

  public ExSourceLine(String line) {
    this.line = line;
  }

  public static ExSourceLine line(String line) {
    return new ExSourceLine(line);
  }

  public String line() {
    return line;
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + line);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
