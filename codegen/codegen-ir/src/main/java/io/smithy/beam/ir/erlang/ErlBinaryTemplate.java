package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlBinaryTemplate implements ErlExpr {
  private final List<ErlBinarySegment> segments;

  public ErlBinaryTemplate(List<ErlBinarySegment> segments) {
    this.segments = List.copyOf(segments);
  }

  public static ErlBinaryTemplate binaryTemplate(ErlBinarySegment... segments) {
    return new ErlBinaryTemplate(List.of(segments));
  }

  public List<ErlBinarySegment> segments() {
    return segments;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder("<<");
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(segmentString(segments.get(i)));
    }
    sb.append(">>");
    return List.of(sb.toString());
  }

  private static String segmentString(ErlBinarySegment segment) {
    if (segment instanceof ErlBinaryText text) {
      return text.asString();
    }
    return ((ErlBinaryExpr) segment).asString();
  }
}
