package io.smithy.beam.ir.erlang;

public final class ErlComprehensionFilter implements ErlComprehensionQual {
  private final ErlExpr filter;

  public ErlComprehensionFilter(ErlExpr filter) {
    this.filter = filter;
  }

  public ErlExpr filter() {
    return filter;
  }
}
