package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlModuleDoc implements ErlPreambleEntry {
  private final String text;

  public ErlModuleDoc(String text) {
    this.text = text;
  }

  public String text() {
    return text;
  }

  public static ErlModuleDoc moduleDoc(String text) {
    return new ErlModuleDoc(text);
  }

  @Override
  public List<String> lines(int indent) {
    return renderDocAttribute("moduledoc", text, indent);
  }

  private static List<String> renderDocAttribute(String attribute, String text, int indent) {
    String head = IrObject.indent(indent) + "-" + attribute;
    if (!text.contains("\n")) {
      return List.of(head + " " + ErlString.string(text).asString() + ".");
    }
    List<String> out = new ArrayList<>();
    out.add(head + " \"\"\"");
    for (String line : text.split("\n", -1)) {
      out.add(IrObject.indent(indent) + line);
    }
    out.add(IrObject.indent(indent) + "\"\"\".");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
