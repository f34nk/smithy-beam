package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExTypesModule implements IrObject {
  private final String moduleName;
  private final List<ExPreambleEntry> preamble;
  private final List<ExModuleEntry> entries;
  private final List<ExFunction> functions;

  public ExTypesModule(
      String moduleName,
      List<ExPreambleEntry> preamble,
      List<ExModuleEntry> entries,
      List<ExFunction> functions) {
    this.moduleName = moduleName;
    this.preamble = List.copyOf(preamble);
    this.entries = List.copyOf(entries);
    this.functions = List.copyOf(functions);
  }

  public static ExTypesModule typesModule(
      String moduleName, List<ExPreambleEntry> preamble, List<ExModuleEntry> entries) {
    return typesModule(moduleName, preamble, entries, List.of());
  }

  public static ExTypesModule typesModule(
      String moduleName,
      List<ExPreambleEntry> preamble,
      List<ExModuleEntry> entries,
      List<ExFunction> functions) {
    return new ExTypesModule(moduleName, preamble, entries, functions);
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

  public List<ExFunction> functions() {
    return functions;
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
    if (!functions.isEmpty()) {
      out.add("");
    }
    for (int i = 0; i < functions.size(); i++) {
      if (i > 0) {
        out.add("");
      }
      out.addAll(functions.get(i).lines(indent + 1));
    }
    out.add(IrObject.indent(indent) + "end");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
