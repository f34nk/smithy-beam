package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExMap implements ExExpr {
  private final List<ExMapEntry> entries;

  public ExMap(List<ExMapEntry> entries) {
    this.entries = List.copyOf(entries);
  }

  public static ExMap map(ExMapEntry... entries) {
    return new ExMap(List.of(entries));
  }

  public List<ExMapEntry> entries() {
    return entries;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (entries.isEmpty()) {
      return List.of(IrObject.indent(indent) + "%{}");
    }
    if (entries.size() <= 1) {
      return List.of(IrObject.indent(indent) + renderInline());
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "%{");
    for (int i = 0; i < entries.size(); i++) {
      String suffix = (i < entries.size() - 1) ? "," : "";
      out.add(IrObject.indent(indent + 1) + entries.get(i).asString() + suffix);
    }
    out.add(IrObject.indent(indent) + "}");
    return out;
  }

  private String renderInline() {
    StringBuilder sb = new StringBuilder("%{");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(entries.get(i).asString());
    }
    sb.append('}');
    return sb.toString();
  }
}
