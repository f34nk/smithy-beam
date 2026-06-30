package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExModuleAssignAttr implements ExModuleAttribute {
  private final String name;
  private final ExExpr value;

  public ExModuleAssignAttr(String name, ExExpr value) {
    this.name = name;
    this.value = value;
  }

  public static ExModuleAssignAttr assign(String name, ExExpr value) {
    return new ExModuleAssignAttr(name, value);
  }

  public String name() {
    return name;
  }

  public ExExpr value() {
    return value;
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + "@" + name + " " + value.asString());
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
