package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlRecordAccess implements ErlExpr {
  private final String recordName;
  private final String fieldName;
  private final ErlExpr record;

  public ErlRecordAccess(String recordName, String fieldName, ErlExpr record) {
    this.recordName = recordName;
    this.fieldName = fieldName;
    this.record = record;
  }

  public static ErlRecordAccess recordAccess(ErlExpr record, String recordName, String fieldName) {
    return new ErlRecordAccess(recordName, fieldName, record);
  }

  public String recordName() {
    return recordName;
  }

  public String fieldName() {
    return fieldName;
  }

  public ErlExpr record() {
    return record;
  }

  @Override
  public List<String> lines() {
    return List.of(record.asString() + "#" + recordName + "." + fieldName);
  }
}
