package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

final class ExLayout {
  static final int HEREDOC_BODY_INDENT_LEVELS = 1;

  private ExLayout() {}

  static List<String> indentLines(List<String> lines, int depth) {
    if (depth == 0) {
      return lines;
    }
    String prefix = IrObject.indent(depth);
    List<String> out = new ArrayList<>(lines.size());
    for (String line : lines) {
      out.add(prefix + line);
    }
    return out;
  }

  static String renderAtom(String value) {
    if (value.startsWith(":") || Character.isDigit(value.charAt(0))) {
      return value;
    }
    if (value.contains(" ") || value.contains("-")) {
      return ":\"" + escapeDoubleQuoted(value.substring(1)) + "\"";
    }
    return value.startsWith(":") ? value : ":" + value;
  }

  static String renderString(String value) {
    return "\"" + escapeDoubleQuoted(value) + "\"";
  }

  static List<String> renderHashComment(String text, int indent) {
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

  static List<String> renderDocAttribute(String attribute, String text, int indent) {
    String head = IrObject.indent(indent) + attribute;
    if (!text.contains("\n")) {
      return List.of(head + " " + renderString(text));
    }
    List<String> out = new ArrayList<>();
    out.add(head + " \"\"\"");
    for (String line : text.split("\n", -1)) {
      if (line.isEmpty()) {
        out.add("");
      } else {
        out.add(IrObject.indent(indent + HEREDOC_BODY_INDENT_LEVELS) + line);
      }
    }
    out.add(IrObject.indent(indent) + "\"\"\"");
    return out;
  }

  private static String escapeDoubleQuoted(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
