package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExKeyword implements ExExpr {
  private final List<ExMapEntry> entries;

  public ExKeyword(List<ExMapEntry> entries) {
    this.entries = List.copyOf(entries);
  }

  public static ExKeyword keyword(ExMapEntry... entries) {
    return new ExKeyword(List.of(entries));
  }

  public List<ExMapEntry> entries() {
    return entries;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      ExMapEntry entry = entries.get(i);
      sb.append(renderKeywordEntry(entry));
    }
    sb.append(']');
    return List.of(sb.toString());
  }

  private static String renderKeywordEntry(ExMapEntry entry) {
    if (entry.key() instanceof ExAtom atom) {
      String value = atom.value();
      if (value.startsWith(":")) {
        return value.substring(1) + ": " + entry.value().asString();
      }
      return value + ": " + entry.value().asString();
    }
    return entry.asString();
  }
}
