package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExNestedModule implements ExModuleEntry {
  private final String name;
  private final List<ExPreambleEntry> preamble;
  private final List<ExModuleEntry> entries;
  private final List<ExFunction> functions;

  public ExNestedModule(
      String name,
      List<ExPreambleEntry> preamble,
      List<ExModuleEntry> entries,
      List<ExFunction> functions) {
    this.name = name;
    this.preamble = List.copyOf(preamble);
    this.entries = List.copyOf(entries);
    this.functions = List.copyOf(functions);
  }

  public static ExNestedModule nestedModule(
      String name,
      List<ExPreambleEntry> preamble,
      List<ExModuleEntry> entries,
      List<ExFunction> functions) {
    return new ExNestedModule(name, preamble, entries, functions);
  }

  public String name() {
    return name;
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

  public boolean hasDefstruct() {
    for (ExModuleEntry entry : entries) {
      if (entry instanceof ExDefstruct) {
        return true;
      }
    }
    return false;
  }

  public int defstructLiteralSizeEstimate() {
    if (!hasDefstruct()) {
      return 0;
    }
    int size = 0;
    for (ExModuleEntry entry : entries) {
      if (entry instanceof ExDefstruct defstruct) {
        for (String field : defstruct.fields()) {
          size += field.length();
        }
      }
      if (entry instanceof ExTypeDef typeDef && typeDef.isModuleStructType()) {
        for (String line : typeDef.structureFieldLines()) {
          size += line.length();
        }
      }
    }
    return size;
  }

  public boolean isEnumModule() {
    if (hasDefstruct()) {
      return false;
    }
    for (ExModuleEntry entry : entries) {
      if (entry instanceof ExDefexception) {
        return false;
      }
    }
    boolean hasTypeAlias =
        entries.stream().anyMatch(e -> e instanceof ExTypeDef td && "t".equals(td.name()));
    return hasTypeAlias && !functions.isEmpty();
  }

  public int enumModuleSizeEstimate() {
    if (!isEnumModule()) {
      return 0;
    }
    int size = 0;
    for (ExModuleEntry entry : entries) {
      if (entry instanceof ExTypeDef td) {
        size += td.body().length();
      }
    }
    for (ExFunction fn : functions) {
      for (String line : fn.lines(0)) {
        size += line.length();
      }
    }
    return size;
  }

  public ExTypesModule asTopLevelModule(String parentModuleName) {
    List<ExModuleEntry> body = new ArrayList<>(entries);
    body.addAll(functions);
    return ExTypesModule.typesModule(parentModuleName + "." + name, preamble, body);
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    out.add("");
    out.add(IrObject.indent(indent) + "defmodule " + name + " do");
    for (ExPreambleEntry item : preamble) {
      out.addAll(item.lines(indent + 1));
    }
    for (ExModuleEntry entry : entries) {
      if (!(entry instanceof ExBlankLine)) {
        out.add("");
      }
      out.addAll(entry.lines(indent + 1));
    }
    for (int i = 0; i < functions.size(); i++) {
      if (i > 0 || !entries.isEmpty()) {
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
