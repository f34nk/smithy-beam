package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExStructUpdate implements ExExpr {
  private final ExExpr struct;
  private final String moduleName;
  private final List<ExMapEntry> fields;

  public ExStructUpdate(ExExpr struct, String moduleName, List<ExMapEntry> fields) {
    this.struct = struct;
    this.moduleName = moduleName;
    this.fields = List.copyOf(fields);
  }

  public static ExStructUpdate structUpdate(ExExpr struct, String moduleName, ExMapEntry... fields) {
    return new ExStructUpdate(struct, moduleName, List.of(fields));
  }

  public ExExpr struct() {
    return struct;
  }

  public String moduleName() {
    return moduleName;
  }

  public List<ExMapEntry> fields() {
    return fields;
  }

  @Override
  public List<String> lines(int indent) {
    if (fields.size() <= 1) {
      return List.of(IrObject.indent(indent) + renderInline());
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "%" + moduleName + "{" + struct.asString() + " |");
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

  private String renderInline() {
    StringBuilder sb = new StringBuilder("%").append(moduleName).append('{');
    sb.append(struct.asString()).append(" | ");
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

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
