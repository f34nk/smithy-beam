package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlFunctionDoc implements IrObject {
  private final String text;

  public ErlFunctionDoc(String text) {
    this.text = text;
  }

  public String text() {
    return text;
  }

  public static ErlFunctionDoc functionDoc(String text) {
    return new ErlFunctionDoc(text);
  }

  @Override
  public List<String> lines(int indent) {
    String marker = IrObject.indent(indent) + "%%";
    if (!text.contains("\n")) {
      return List.of(marker + " @doc " + text);
    }
    List<String> out = new ArrayList<>();
    out.add(marker + " @doc");
    for (String line : text.split("\n", -1)) {
      if (line.isEmpty()) {
        out.add(marker);
      } else {
        out.add(marker + " " + line);
      }
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
