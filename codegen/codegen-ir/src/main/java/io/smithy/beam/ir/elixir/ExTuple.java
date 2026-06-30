package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExTuple implements ExExpr {
  private final List<ExExpr> elements;

  public ExTuple(List<ExExpr> elements) {
    this.elements = List.copyOf(elements);
  }

  public static ExTuple tuple(ExExpr... elements) {
    return new ExTuple(List.of(elements));
  }

  public List<ExExpr> elements() {
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
