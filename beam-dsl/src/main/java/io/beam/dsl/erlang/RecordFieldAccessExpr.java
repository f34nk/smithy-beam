package io.beam.dsl.erlang;

public record RecordFieldAccessExpr(Expression receiver, String recordName, String fieldName)
    implements Expression {

  public static RecordFieldAccessExpr of(Expression receiver, String recordName, String fieldName) {
    return new RecordFieldAccessExpr(receiver, recordName, fieldName);
  }
}
