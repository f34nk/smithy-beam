package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExModuledoc implements ExPreambleEntry {
  private final String text;

  private ExModuledoc(String text) {
    this.text = text;
  }

  public static ExModuledoc moduledoc(String text) {
    return new ExModuledoc(text);
  }

  public String text() {
    return text;
  }

  @Override
  public List<String> lines(int indent) {
    return renderDocAttribute("@moduledoc", text, indent);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  static List<String> renderDocAttribute(String attribute, String text, int indent) {
    String head = IrObject.indent(indent) + attribute;
    if (!text.contains("\n")) {
      return List.of(head + " " + ExString.renderString(text));
    }
    List<String> out = new ArrayList<>();
    out.add(head + " \"\"\"");
    for (String line : text.split("\n", -1)) {
      if (line.isEmpty()) {
        out.add("");
      } else {
        out.add(IrObject.indent(indent + ExComment.HEREDOC_BODY_INDENT_LEVELS) + line);
      }
    }
    out.add(IrObject.indent(indent) + "\"\"\"");
    return out;
  }
}
