package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlIfndef implements ErlHeaderEntry {
  private final String name;

  public ErlIfndef(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + "-ifndef(" + name + ").");
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
