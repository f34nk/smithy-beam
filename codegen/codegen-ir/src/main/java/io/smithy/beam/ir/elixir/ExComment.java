package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExComment implements ExPreambleEntry {
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
    return ExLayout.renderHashComment(text, indent);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
