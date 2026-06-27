package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlDefine implements ErlHeaderEntry {
  private final String name;
  private final String value;

  public ErlDefine(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public String name() {
    return name;
  }

  public String value() {
    return value;
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + "-define(" + name + ", " + value + ").");
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
