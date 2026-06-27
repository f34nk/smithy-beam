package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

/** Multi-line Elixir expression text with indent-aware rendering. */
public final class ExCapturedBlock implements ExExpr {
  private final String text;

  public ExCapturedBlock(String text) {
    this.text = text == null ? "" : text.strip();
  }

  public static ExCapturedBlock capturedBlock(String text) {
    return new ExCapturedBlock(text);
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
