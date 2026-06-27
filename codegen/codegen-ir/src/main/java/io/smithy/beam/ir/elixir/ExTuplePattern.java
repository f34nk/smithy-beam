package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExTuplePattern implements ExPattern {
  private final List<ExPattern> elements;

  public ExTuplePattern(List<ExPattern> elements) {
    this.elements = List.copyOf(elements);
  }

  public static ExTuplePattern tuple(ExPattern... elements) {
    return new ExTuplePattern(List.of(elements));
  }

  public List<ExPattern> elements() {
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
