package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExBinaryTemplate implements ExExpr {
  private final List<ExExpr> segments;

  public ExBinaryTemplate(List<ExExpr> segments) {
    this.segments = List.copyOf(segments);
  }

  public static ExBinaryTemplate binaryTemplate(ExExpr... segments) {
    return new ExBinaryTemplate(List.of(segments));
  }

  public List<ExExpr> segments() {
    return segments;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder("<<");
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(segments.get(i).asString());
    }
    sb.append(">>");
    return List.of(sb.toString());
  }
}
