package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExDoc implements IrObject {
  private final String text;

  private ExDoc(String text) {
    this.text = text;
  }

  public static ExDoc doc(String text) {
    return new ExDoc(text);
  }

  public String text() {
    return text;
  }

  @Override
  public List<String> lines(int indent) {
    return ExModuledoc.renderDocAttribute("@doc", text, indent);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
