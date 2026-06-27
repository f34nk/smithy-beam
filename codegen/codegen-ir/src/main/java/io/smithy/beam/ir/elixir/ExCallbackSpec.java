package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExCallbackSpec implements IrObject {
  private final String name;
  private final List<String> params;
  private final String returnType;

  public ExCallbackSpec(String name, List<String> params, String returnType) {
    this.name = name;
    this.params = List.copyOf(params);
    this.returnType = returnType;
  }

  public static ExCallbackSpec callbackSpec(String name, List<String> params, String returnType) {
    return new ExCallbackSpec(name, params, returnType);
  }

  public String name() {
    return name;
  }

  public List<String> params() {
    return params;
  }

  public String returnType() {
    return returnType;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "@callback " + name + "(");
    int paramIndent = indent + 6;
    for (int i = 0; i < params.size(); i++) {
      String suffix = (i < params.size() - 1) ? "," : "";
      out.add(IrObject.indent(paramIndent) + params.get(i) + suffix);
    }
    out.add(IrObject.indent(indent) + ") ::");
    out.add(IrObject.indent(indent + 1) + returnType);
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
