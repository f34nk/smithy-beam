package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExTypedoc implements ExModuleEntry {
  private final String text;

  private ExTypedoc(String text) {
    this.text = text;
  }

  public static ExTypedoc typedoc(String text) {
    return new ExTypedoc(text);
  }

  public String text() {
    return text;
  }

  @Override
  public List<String> lines(int indent) {
    return ExModuledoc.renderDocAttribute("@typedoc", text, indent);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
