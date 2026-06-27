package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExImportAttr implements ExModuleAttribute {
  private final String module;
  private final List<String> functions;

  public ExImportAttr(String module, List<String> functions) {
    this.module = module;
    this.functions = List.copyOf(functions);
  }

  public static ExImportAttr importFunctions(String module, List<String> functions) {
    return new ExImportAttr(module, functions);
  }

  public String module() {
    return module;
  }

  public List<String> functions() {
    return functions;
  }

  @Override
  public List<String> lines(int indent) {
    if (functions.isEmpty()) {
      return List.of(IrObject.indent(indent) + "import " + module);
    }
    return List.of(
        IrObject.indent(indent) + "import " + module + ", only: [" + String.join(", ", functions) + "]");
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
