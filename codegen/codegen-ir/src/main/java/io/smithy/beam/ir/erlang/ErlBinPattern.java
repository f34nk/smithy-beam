package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlBinPattern implements ErlPattern {
  private final String syntax;

  public ErlBinPattern(String syntax) {
    this.syntax = syntax;
  }

  public static ErlBinPattern binPattern(String syntax) {
    return new ErlBinPattern(syntax);
  }

  public String syntax() {
    return syntax;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  public List<String> lines(int indent) {
    String inline = "<<" + syntax + ">>";
    if (!ErlFormat.exceedsLineLimit(indent, inline)) {
      return List.of(inline);
    }
    int split = syntax.indexOf(", S:2/binary");
    if (split < 0) {
      return List.of(inline);
    }
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, "<<" + syntax.substring(0, split + 1)));
    out.add(ErlFormat.prefixed(indent + 1, syntax.substring(split + 2) + ">>"));
    return out;
  }
}
