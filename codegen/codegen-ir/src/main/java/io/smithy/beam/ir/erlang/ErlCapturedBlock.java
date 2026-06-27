package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

/** Multi-line Erlang expression text with indent-aware rendering. */
public final class ErlCapturedBlock implements ErlExpr {
  private final String text;

  public ErlCapturedBlock(String text) {
    this.text = text == null ? "" : text.strip();
  }

  public static ErlCapturedBlock capturedBlock(String text) {
    return new ErlCapturedBlock(text);
  }

  public String text() {
    return text;
  }

  @Override
  public List<String> lines(int indent) {
    if (text.isEmpty()) {
      return List.of();
    }
    String[] lines = text.split("\n", -1);
    List<String> out = new ArrayList<>();
    for (String line : lines) {
      out.add(IrObject.indent(indent) + line);
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
