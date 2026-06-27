package io.smithy.beam.ir.erlang;

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
    String single = IrObject.indent(indent) + name + " = " + expr;
    if (single.length() <= LINE_LIMIT) {
      return List.of(single);
    }
    int open = expr.indexOf('(');
    if (open < 0) {
      return List.of(single);
    }
    return List.of(
        IrObject.indent(indent) + name + " = " + expr.substring(0, open + 1),
        IrObject.indent(indent + 1) + expr.substring(open + 1));
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
