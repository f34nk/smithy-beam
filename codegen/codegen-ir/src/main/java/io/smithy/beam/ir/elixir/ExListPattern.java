package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExListPattern implements ExPattern {
  private final List<ExPattern> elements;
  private final ExPattern tailOrNull;

  public ExListPattern(List<ExPattern> elements, ExPattern tailOrNull) {
    this.elements = List.copyOf(elements);
    this.tailOrNull = tailOrNull;
  }

  public static ExListPattern list(ExPattern... elements) {
    return new ExListPattern(List.of(elements), null);
  }

  public static ExListPattern cons(ExPattern head, ExPattern tail) {
    return new ExListPattern(List.of(head), tail);
  }

  public List<ExPattern> elements() {
    return elements;
  }

  public ExPattern tailOrNull() {
    return tailOrNull;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(elements.get(i).asString());
    }
    if (tailOrNull != null) {
      if (!elements.isEmpty()) {
        sb.append(" | ");
      }
      sb.append(tailOrNull.asString());
    }
    sb.append(']');
    return List.of(sb.toString());
  }
}
