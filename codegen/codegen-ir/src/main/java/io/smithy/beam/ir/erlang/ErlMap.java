package io.smithy.beam.ir.erlang;

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
    StringBuilder sb = new StringBuilder("#{");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(entries.get(i).asString());
    }
    sb.append('}');
    return List.of(sb.toString());
  }
}
