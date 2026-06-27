package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlTypeHeader implements IrObject {
  private final String name;
  private final List<ErlPreambleEntry> preamble;
  private final List<ErlHeaderEntry> entries;
  private final boolean separateEntries;

  public ErlTypeHeader(
      String name,
      List<ErlPreambleEntry> preamble,
      List<ErlHeaderEntry> entries,
      boolean separateEntries) {
    this.name = name;
    this.preamble = List.copyOf(preamble);
    this.entries = List.copyOf(entries);
    this.separateEntries = separateEntries;
  }

  public static ErlTypeHeader typeHeader(
      String name, List<ErlPreambleEntry> preamble, List<ErlHeaderEntry> entries) {
    return typeHeader(name, preamble, entries, true);
  }

  public static ErlTypeHeader typeHeader(
      String name,
      List<ErlPreambleEntry> preamble,
      List<ErlHeaderEntry> entries,
      boolean separateEntries) {
    return new ErlTypeHeader(name, preamble, entries, separateEntries);
  }

  public String name() {
    return name;
  }

  public List<ErlPreambleEntry> preamble() {
    return preamble;
  }

  public List<ErlHeaderEntry> entries() {
    return entries;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    for (ErlPreambleEntry item : preamble) {
      out.addAll(item.lines(indent));
    }
    for (int i = 0; i < entries.size(); i++) {
      if (separateEntries && i > 0 && shouldSeparateEntries(entries.get(i - 1), entries.get(i))) {
        out.add("");
      }
      out.addAll(entries.get(i).lines(indent));
    }
    return out;
  }

  private static boolean shouldSeparateEntries(ErlHeaderEntry previous, ErlHeaderEntry current) {
    if (previous instanceof ErlRecordDef record && current instanceof ErlTypeDef type) {
      return !record.name().equals(type.name());
    }
    return true;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
