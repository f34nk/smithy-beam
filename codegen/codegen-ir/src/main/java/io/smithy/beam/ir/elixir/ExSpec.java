package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExSpec implements IrObject {
  private final String name;
  private final String params;
  private final String returnType;

  public ExSpec(String name, String params, String returnType) {
    this.name = name;
    this.params = params;
    this.returnType = returnType;
  }

  public static ExSpec functionSpec(String name, String params, String returnType) {
    return new ExSpec(name, params, returnType);
  }

  public String name() {
    return name;
  }

  public String params() {
    return params;
  }

  public String returnType() {
    return returnType;
  }

  @Override
  public List<String> lines(int indent) {
    String signature = name + "(" + params + ") :: " + returnType;
    String line = "@spec " + signature;
    if (line.length() <= IrObject.SPEC_LINE_LIMIT) {
      return List.of(IrObject.indent(indent) + line);
    }
    int split = signature.indexOf(" :: ");
    if (split < 0) {
      return List.of(IrObject.indent(indent) + line);
    }
    return List.of(
        IrObject.indent(indent) + "@spec " + signature.substring(0, split) + " ::",
        IrObject.indent(indent) + "        " + signature.substring(split + 4));
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
