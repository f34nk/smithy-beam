package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExMapUpdate implements ExExpr {
  private final ExExpr map;
  private final List<ExMapEntry> entries;

  public ExMapUpdate(ExExpr map, List<ExMapEntry> entries) {
    this.map = map;
    this.entries = List.copyOf(entries);
  }

  public static ExMapUpdate mapUpdate(ExExpr map, ExMapEntry... entries) {
    return new ExMapUpdate(map, List.of(entries));
  }

  public ExExpr map() {
    return map;
  }

  public List<ExMapEntry> entries() {
    return entries;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder("%{").append(map.asString()).append(" | ");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(entries.get(i).asString());
    }
    sb.append("}");
    return List.of(sb.toString());
  }
}
