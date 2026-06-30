package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExStructPattern implements ExPattern {
  private final String moduleName;
  private final List<ExStructFieldPattern> fields;
  private final String aliasOrNull;

  public ExStructPattern(String moduleName, List<ExStructFieldPattern> fields, String aliasOrNull) {
    this.moduleName = moduleName;
    this.fields = List.copyOf(fields);
    this.aliasOrNull = aliasOrNull;
  }

  public ExStructPattern(String moduleName, List<ExStructFieldPattern> fields) {
    this(moduleName, fields, null);
  }

  public static ExStructPattern struct(String moduleName, List<ExStructFieldPattern> fields) {
    return new ExStructPattern(moduleName, fields);
  }

  public static ExStructPattern struct(String moduleName, ExStructFieldPattern... fields) {
    return new ExStructPattern(moduleName, List.of(fields));
  }

  public static ExStructPattern structFunctionHead(
      String alias, String moduleName, List<String> fieldNames) {
    List<ExStructFieldPattern> fields =
        fieldNames.stream().map(ExStructFieldPattern::field).toList();
    return new ExStructPattern(moduleName, fields, alias);
  }

  public String moduleName() {
    return moduleName;
  }

  public List<ExStructFieldPattern> fields() {
    return fields;
  }

  public String aliasOrNull() {
    return aliasOrNull;
  }

  public boolean breaksFunctionHead() {
    return fields.size() > 1;
  }

  @Override
  public List<String> lines() {
    return List.of(renderInline());
  }

  public List<String> functionHeadLines(
      int indent, String keyword, String name, String whenClauseOrNull, boolean blockBody) {
    List<String> out = new ArrayList<>();
    String aliasPrefix = aliasOrNull != null ? aliasOrNull + " = " : "";
    out.add(
        IrObject.indent(indent)
            + keyword
            + " "
            + name
            + "("
            + aliasPrefix
            + "%"
            + moduleName
            + "{");
    for (int i = 0; i < fields.size(); i++) {
      String suffix = (i < fields.size() - 1) ? "," : "";
      out.add(IrObject.indent(indent + 1) + fields.get(i).asString() + suffix);
    }
    String close = IrObject.indent(indent) + "})";
    if (whenClauseOrNull != null) {
      close += " " + whenClauseOrNull;
    }
    if (blockBody) {
      close += " do";
    }
    out.add(close);
    return out;
  }

  private String renderInline() {
    StringBuilder sb = new StringBuilder();
    if (aliasOrNull != null) {
      sb.append(aliasOrNull).append(" = ");
    }
    sb.append('%').append(moduleName).append('{');
    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(fields.get(i).asString());
    }
    sb.append('}');
    return sb.toString();
  }
}
