package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExImplAttr implements ExModuleAttribute {
  private final boolean implTrue;

  public ExImplAttr(boolean implTrue) {
    this.implTrue = implTrue;
  }

  public static ExImplAttr implTrue() {
    return new ExImplAttr(true);
  }

  @Override
  public List<String> lines(int indent) {
    return List.of(IrObject.indent(indent) + (implTrue ? "@impl true" : "@impl false"));
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
