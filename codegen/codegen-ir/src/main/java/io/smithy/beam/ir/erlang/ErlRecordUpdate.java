package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlRecordUpdate implements ErlExpr {
  private final ErlRecord delegate;

  public ErlRecordUpdate(ErlExpr record, String recordName, List<ErlRecordField> updates) {
    this.delegate = new ErlRecord(recordName, record, updates);
  }

  public static ErlRecordUpdate recordUpdate(
      ErlExpr record, String recordName, ErlRecordField... updates) {
    return new ErlRecordUpdate(record, recordName, List.of(updates));
  }

  public ErlRecord delegate() {
    return delegate;
  }

  @Override
  public List<String> lines(int indent) {
    return delegate.lines(indent);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
