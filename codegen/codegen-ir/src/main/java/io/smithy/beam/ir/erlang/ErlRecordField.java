package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlRecordField implements IrObject {
  private final String name;
  private final ErlExpr value;

  public ErlRecordField(String name, ErlExpr value) {
    this.name = name;
    this.value = value;
  }

  public static ErlRecordField field(String name, ErlExpr value) {
    return new ErlRecordField(name, value);
  }

  public String name() {
    return name;
  }

  public ErlExpr value() {
    return value;
  }

  private static final int LINE_LIMIT = 100;

  @Override
  public List<String> lines(int indent) {
    String expr = value.asString();
    String single = ErlFormat.prefixed(indent, name + " = " + expr);
    if (single.length() <= LINE_LIMIT) {
      return List.of(single);
    }
    if (value instanceof ErlList list && list.tailOrNull() == null) {
      List<String> out = new ArrayList<>();
      out.add(ErlFormat.prefixed(indent, name + " = ["));
      List<ErlExpr> elements = list.elements();
      for (int i = 0; i < elements.size(); i++) {
        String suffix = i < elements.size() - 1 ? "," : "";
        out.add(ErlFormat.prefixed(indent + 1, elements.get(i).asString() + suffix));
      }
      out.add(ErlFormat.prefixed(indent, "]"));
      return out;
    }
    List<String> valueLines = ErlFormat.renderExprLines(value, indent);
    if (valueLines.size() == 1) {
      int open = expr.indexOf('(');
      if (open < 0) {
        return List.of(single);
      }
      return List.of(
          ErlFormat.prefixed(indent, name + " = " + expr.substring(0, open + 1)),
          ErlFormat.prefixed(indent + 1, expr.substring(open + 1)));
    }
    List<String> out = new ArrayList<>();
    String first = valueLines.get(0);
    if (first.startsWith(ErlFormat.prefixed(indent, ""))) {
      first = first.substring(ErlFormat.prefixed(indent, "").length());
    }
    out.add(ErlFormat.prefixed(indent, name + " = " + first));
    out.addAll(valueLines.subList(1, valueLines.size()));
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
