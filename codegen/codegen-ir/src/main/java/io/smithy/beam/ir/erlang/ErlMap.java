package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlMap implements ErlExpr {
  private final List<ErlMapEntry> entries;

  public ErlMap(List<ErlMapEntry> entries) {
    this.entries = List.copyOf(entries);
  }

  public static ErlMap map(ErlMapEntry... entries) {
    return new ErlMap(List.of(entries));
  }

  public List<ErlMapEntry> entries() {
    return entries;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    String inline = inlineAsString();
    if (!ErlFormat.exceedsLineLimit(indent, inline)) {
      return List.of(inline);
    }
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, "#{"));
    for (int i = 0; i < entries.size(); i++) {
      String suffix = i < entries.size() - 1 ? "," : "";
      out.add(ErlFormat.prefixed(indent + 1, entries.get(i).asString() + suffix));
    }
    out.add(ErlFormat.prefixed(indent, "}"));
    return out;
  }

  private String inlineAsString() {
    StringBuilder sb = new StringBuilder("#{");
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
