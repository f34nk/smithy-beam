package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExDefstruct implements ExModuleEntry {
  static final int MULTILINE_THRESHOLD = 4;

  private final List<String> fields;
  private final boolean keywordForm;

  public ExDefstruct(List<String> fields) {
    this(fields, false);
  }

  private ExDefstruct(List<String> fields, boolean keywordForm) {
    this.fields = List.copyOf(fields);
    this.keywordForm = keywordForm;
  }

  public static ExDefstruct defstruct(List<String> fields) {
    return new ExDefstruct(fields);
  }

  public static ExDefstruct defstructKeywords(List<String> keywordFields) {
    return new ExDefstruct(keywordFields, true);
  }

  public List<String> fields() {
    return fields;
  }

  @Override
  public List<String> lines(int indent) {
    if (keywordForm) {
      return defstructKeywordLines(indent);
    }
    if (fields.size() <= MULTILINE_THRESHOLD) {
      return List.of(
          IrObject.indent(indent) + "defstruct [" + String.join(", ", fields) + "]");
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "defstruct [");
    for (int i = 0; i < fields.size(); i++) {
      String suffix = (i < fields.size() - 1) ? "," : "";
      out.add(IrObject.indent(indent + 1) + fields.get(i) + suffix);
    }
    out.add(IrObject.indent(indent) + "]");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  private List<String> defstructKeywordLines(int indent) {
    if (fields.isEmpty()) {
      return List.of(IrObject.indent(indent) + "defstruct []");
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "defstruct " + fields.get(0) + ",");
    for (int i = 1; i < fields.size(); i++) {
      String suffix = (i < fields.size() - 1) ? "," : "";
      out.add(IrObject.indent(indent) + "             " + fields.get(i) + suffix);
    }
    return out;
  }
}
