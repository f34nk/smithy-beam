package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExModuledoc implements IrObject {
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
    return ExLayout.renderDocAttribute("@moduledoc", text, indent);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
