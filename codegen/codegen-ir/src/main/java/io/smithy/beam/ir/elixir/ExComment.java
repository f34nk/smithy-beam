package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExComment implements ExPreambleEntry, ExModuleEntry {
  static final int HEREDOC_BODY_INDENT_LEVELS = 1;

  private final String text;

  private ExComment(String text) {
    this.text = text;
  }

  public static ExComment comment(String text) {
    return new ExComment(text);
  }

  public String text() {
    return text;
  }

  @Override
  public List<String> lines(int indent) {
    String marker = IrObject.indent(indent) + "#";
    if (!text.contains("\n")) {
      if (text.isEmpty()) {
        return List.of(marker);
      }
      return List.of(marker + " " + text);
    }
    List<String> out = new ArrayList<>();
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
