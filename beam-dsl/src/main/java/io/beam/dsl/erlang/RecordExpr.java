package io.beam.dsl.erlang;

import java.util.List;

public record RecordExpr(String name, Expression base, List<RecordField> fields)
    implements Expression {

  public static RecordExpr of(String name, List<RecordField> fields) {
    return new RecordExpr(name, null, fields);
  }

  public static RecordExpr update(Expression base, String name, List<RecordField> fields) {
    return new RecordExpr(name, base, fields);
  }
}
