package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlTuplePattern implements ErlPattern {
  private final List<ErlPattern> elements;

  public ErlTuplePattern(List<ErlPattern> elements) {
    this.elements = List.copyOf(elements);
  }

  public static ErlTuplePattern tuplePattern(ErlPattern... elements) {
    return new ErlTuplePattern(List.of(elements));
  }

  public List<ErlPattern> elements() {
    return elements;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(elements.get(i).asString());
    }
    sb.append('}');
    return List.of(sb.toString());
  }
}
