package io.smithy.beam.ir.elixir;

public final class ExForFilter {
  private final ExExpr filter;

  public ExForFilter(ExExpr filter) {
    this.filter = filter;
  }

  public static ExForFilter filter(ExExpr filter) {
    return new ExForFilter(filter);
  }

  public ExExpr filter() {
    return filter;
  }
}
