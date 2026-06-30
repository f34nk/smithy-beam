package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExAliasAttr implements ExModuleAttribute {
  private final String module;
  private final String asOrNull;

  public ExAliasAttr(String module, String asOrNull) {
    this.module = module;
    this.asOrNull = asOrNull;
  }

  public static ExAliasAttr alias(String module) {
    return new ExAliasAttr(module, null);
  }

  public static ExAliasAttr alias(String module, String as) {
    return new ExAliasAttr(module, as);
  }

  public String module() {
    return module;
  }

  public String asOrNull() {
    return asOrNull;
  }

  @Override
  public List<String> lines(int indent) {
    if (asOrNull == null) {
      return List.of(IrObject.indent(indent) + "alias " + module);
    }
    return List.of(IrObject.indent(indent) + "alias " + module + ", as: " + asOrNull);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
