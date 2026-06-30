package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExModule implements IrObject {
  private final String moduleName;
  private final List<ExPreambleEntry> preamble;
  private final List<ExModuleAttribute> attributes;
  private final List<ExCallbackSpec> callbackSpecs;
  private final List<ExFunction> functions;
  private final List<ExModuleEntry> nestedEntries;

  public ExModule(
      String moduleName,
      List<ExPreambleEntry> preamble,
      List<ExModuleAttribute> attributes,
      List<ExCallbackSpec> callbackSpecs,
      List<ExFunction> functions,
      List<ExModuleEntry> nestedEntries) {
    this.moduleName = moduleName;
    this.preamble = List.copyOf(preamble);
    this.attributes = List.copyOf(attributes);
    this.callbackSpecs = List.copyOf(callbackSpecs);
    this.functions = List.copyOf(functions);
    this.nestedEntries = List.copyOf(nestedEntries);
  }

  public static ExModule module(
      String moduleName,
      List<ExPreambleEntry> preamble,
      List<ExModuleAttribute> attributes,
      List<ExFunction> functions) {
    return new ExModule(moduleName, preamble, attributes, List.of(), functions, List.of());
  }

  public static ExModule module(
      String moduleName,
      List<ExPreambleEntry> preamble,
      List<ExModuleAttribute> attributes,
      List<ExCallbackSpec> callbackSpecs,
      List<ExFunction> functions) {
    return new ExModule(moduleName, preamble, attributes, callbackSpecs, functions, List.of());
  }

  public static ExModule module(
      String moduleName,
      List<ExPreambleEntry> preamble,
      List<ExModuleAttribute> attributes,
      List<ExCallbackSpec> callbackSpecs,
      List<ExFunction> functions,
      List<ExModuleEntry> nestedEntries) {
    return new ExModule(moduleName, preamble, attributes, callbackSpecs, functions, nestedEntries);
  }

  public String moduleName() {
    return moduleName;
  }

  public List<ExPreambleEntry> preamble() {
    return preamble;
  }

  public List<ExModuleAttribute> attributes() {
    return attributes;
  }

  public List<ExCallbackSpec> callbackSpecs() {
    return callbackSpecs;
  }

  public List<ExFunction> functions() {
    return functions;
  }

  public List<ExModuleEntry> nestedEntries() {
    return nestedEntries;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "defmodule " + moduleName + " do");
    for (ExPreambleEntry item : preamble) {
      out.addAll(item.lines(indent + 1));
    }
    for (ExModuleAttribute attribute : attributes) {
      out.addAll(attribute.lines(indent + 1));
    }
    for (ExCallbackSpec callback : callbackSpecs) {
      out.addAll(callback.lines(indent + 1));
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
    for (ExModuleEntry entry : nestedEntries) {
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
