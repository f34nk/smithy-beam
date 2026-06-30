package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExRequireAttr implements ExModuleAttribute {
  private final String module;

  public ExRequireAttr(String module) {
    this.module = module;
  }

  public static ExRequireAttr require(String module) {
    return new ExRequireAttr(module);
  }

  public String module() {
    return module;
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + "require " + module);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
