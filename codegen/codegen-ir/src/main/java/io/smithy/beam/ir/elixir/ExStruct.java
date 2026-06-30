package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExStruct implements ExExpr {
  private final String moduleName;
  private final List<ExMapEntry> fields;

  public ExStruct(String moduleName, List<ExMapEntry> fields) {
    this.moduleName = moduleName;
    this.fields = List.copyOf(fields);
  }

  public static ExStruct struct(String moduleName, List<ExMapEntry> fields) {
    return new ExStruct(moduleName, fields);
  }

  public static ExStruct struct(String moduleName, ExMapEntry... fields) {
    return new ExStruct(moduleName, List.of(fields));
  }

  public String moduleName() {
    return moduleName;
  }

  public List<ExMapEntry> fields() {
    return fields;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (fields.isEmpty()) {
      return List.of(IrObject.indent(indent) + "%" + moduleName + "{}");
    }
    if (fields.size() <= 1) {
      return List.of(IrObject.indent(indent) + renderInline());
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "%" + moduleName + "{");
    for (int i = 0; i < fields.size(); i++) {
      ExMapEntry field = fields.get(i);
      String suffix = (i < fields.size() - 1) ? "," : "";
      out.add(
          IrObject.indent(indent + 1)
              + renderFieldKey(field.key())
              + ": "
              + field.value().asString()
              + suffix);
    }
    out.add(IrObject.indent(indent) + "}");
    return out;
  }

  private static String renderFieldKey(ExExpr key) {
    if (key instanceof ExAtom atom) {
      String value = atom.value();
      if (value.startsWith(":")) {
        return value.substring(1);
      }
      return value;
    }
    return key.asString();
  }

  private String renderInline() {
    StringBuilder sb = new StringBuilder("%").append(moduleName).append('{');
    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      ExMapEntry field = fields.get(i);
      sb.append(renderFieldKey(field.key())).append(": ").append(field.value().asString());
    }
    sb.append('}');
    return sb.toString();
  }
}
