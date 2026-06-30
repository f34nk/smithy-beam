package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExBehaviourAttr implements ExModuleAttribute {
  private final String module;

  public ExBehaviourAttr(String module) {
    this.module = module;
  }

  public static ExBehaviourAttr behaviour(String module) {
    return new ExBehaviourAttr(module);
  }

  public String module() {
    return module;
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + "@behaviour " + module);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
