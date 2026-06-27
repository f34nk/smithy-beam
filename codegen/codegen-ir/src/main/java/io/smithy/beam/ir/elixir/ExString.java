package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExString implements ExExpr {
  private final String value;

  private ExString(String value) {
    this.value = value;
  }

  public static ExString string(String value) {
    return new ExString(value);
  }

  public String value() {
    return value;
  }

  @Override
  public List<String> lines() {
    return List.of(renderString(value));
  }

  static String renderString(String value) {
    return "\"" + escapeDoubleQuoted(value) + "\"";
  }

  private static String escapeDoubleQuoted(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
