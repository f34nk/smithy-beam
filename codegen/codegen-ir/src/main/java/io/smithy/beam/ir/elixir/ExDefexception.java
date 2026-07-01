package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExDefexception implements ExModuleEntry {
  private final List<String> keywordFields;

  public ExDefexception(List<String> keywordFields) {
    this.keywordFields = List.copyOf(keywordFields);
  }

  public static ExDefexception defexception(List<String> keywordFields) {
    return new ExDefexception(keywordFields);
  }

  public List<String> keywordFields() {
    return keywordFields;
  }

  @Override
  public List<String> lines(int indent) {
    if (keywordFields.isEmpty()) {
      return List.of(IrObject.indent(indent) + "defexception []");
    }
    List<String> out = new ArrayList<>();
    if (keywordFields.size() == 1) {
      out.add(IrObject.indent(indent) + "defexception " + keywordFields.get(0));
      return out;
    }
    out.add(IrObject.indent(indent) + "defexception " + keywordFields.get(0) + ",");
    for (int i = 1; i < keywordFields.size(); i++) {
      String suffix = (i < keywordFields.size() - 1) ? "," : "";
      out.add(IrObject.indent(indent) + "             " + keywordFields.get(i) + suffix);
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
