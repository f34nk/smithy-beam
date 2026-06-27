package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExAtom implements ExExpr {
  private final String value;

  private ExAtom(String value) {
    this.value = value;
  }

  public static ExAtom atom(String value) {
    return new ExAtom(value);
  }

  public String value() {
    return value;
  }

  @Override
  public List<String> lines() {
    return List.of(renderAtom(value));
  }

  static String renderAtom(String value) {
    if (value.startsWith(":") || Character.isDigit(value.charAt(0))) {
      return value;
    }
    if (value.contains(" ") || value.contains("-")) {
      return ":\"" + escapeDoubleQuoted(value.substring(1)) + "\"";
    }
    return ":" + value;
  }

  private static String escapeDoubleQuoted(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
