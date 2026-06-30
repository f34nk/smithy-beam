package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExNestedModule implements ExModuleEntry {
  private final String name;
  private final List<ExPreambleEntry> preamble;
  private final List<ExModuleAssignAttr> attributes;
  private final List<ExModuleEntry> entries;
  private final List<ExFunction> functions;

  public ExNestedModule(
      String name,
      List<ExPreambleEntry> preamble,
      List<ExModuleAssignAttr> attributes,
      List<ExModuleEntry> entries,
      List<ExFunction> functions) {
    this.name = name;
    this.preamble = List.copyOf(preamble);
    this.attributes = List.copyOf(attributes);
    this.entries = List.copyOf(entries);
    this.functions = List.copyOf(functions);
  }

  public static ExNestedModule nestedModule(
      String name,
      List<ExPreambleEntry> preamble,
      List<ExModuleAssignAttr> attributes,
      List<ExModuleEntry> entries,
      List<ExFunction> functions) {
    return new ExNestedModule(name, preamble, attributes, entries, functions);
  }

  public String name() {
    return name;
  }

  public List<ExPreambleEntry> preamble() {
    return preamble;
  }

  public List<ExModuleAssignAttr> attributes() {
    return attributes;
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
    out.add("");
    out.add(IrObject.indent(indent) + "defmodule " + name + " do");
    for (ExPreambleEntry item : preamble) {
      out.addAll(item.lines(indent + 1));
    }
    for (ExModuleAssignAttr attr : attributes) {
      out.addAll(attr.lines(indent + 1));
    }
    for (ExModuleEntry entry : entries) {
      if (!(entry instanceof ExBlankLine)) {
        out.add("");
      }
      out.addAll(entry.lines(indent + 1));
    }
    for (int i = 0; i < functions.size(); i++) {
      if (i > 0 || !entries.isEmpty() || !attributes.isEmpty()) {
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
