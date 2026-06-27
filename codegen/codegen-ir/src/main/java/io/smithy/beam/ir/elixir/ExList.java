package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExList implements ExExpr {
  private final List<ExExpr> elements;
  private final ExExpr tailOrNull;

  public ExList(List<ExExpr> elements, ExExpr tailOrNull) {
    this.elements = List.copyOf(elements);
    this.tailOrNull = tailOrNull;
  }

  public static ExList list(ExExpr... elements) {
    return new ExList(List.of(elements), null);
  }

  public static ExList cons(ExExpr head, ExExpr tail) {
    return new ExList(List.of(head), tail);
  }

  public List<ExExpr> elements() {
    return elements;
  }

  public ExExpr tailOrNull() {
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
