package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExTypesModule implements IrObject {
  private final String moduleName;
  private final List<ExPreambleEntry> preamble;
  private final List<ExModuleEntry> entries;

  public ExTypesModule(
      String moduleName, List<ExPreambleEntry> preamble, List<ExModuleEntry> entries) {
    this.moduleName = moduleName;
    this.preamble = List.copyOf(preamble);
    this.entries = List.copyOf(entries);
  }

  public static ExTypesModule typesModule(
      String moduleName, List<ExPreambleEntry> preamble, List<ExModuleEntry> entries) {
    return new ExTypesModule(moduleName, preamble, entries);
  }

  public String moduleName() {
    return moduleName;
  }

  public List<ExPreambleEntry> preamble() {
    return preamble;
  }

  public List<ExModuleEntry> entries() {
    return entries;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "defmodule " + moduleName + " do");
    for (ExPreambleEntry item : preamble) {
      out.addAll(item.lines(indent + 1));
    }
    for (ExModuleEntry entry : entries) {
      out.addAll(entry.lines(indent + 1));
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
