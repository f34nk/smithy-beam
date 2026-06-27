package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlList implements ErlExpr {
  private final List<ErlExpr> elements;
  private final ErlExpr tailOrNull;

  public ErlList(List<ErlExpr> elements, ErlExpr tailOrNull) {
    this.elements = List.copyOf(elements);
    this.tailOrNull = tailOrNull;
  }

  public static ErlList list(ErlExpr... elements) {
    return new ErlList(List.of(elements), null);
  }

  public static ErlList cons(ErlExpr head, ErlExpr tail) {
    return new ErlList(List.of(head), tail);
  }

  public static ErlList consList(List<ErlExpr> elements, ErlExpr tail) {
    return new ErlList(elements, tail);
  }

  public List<ErlExpr> elements() {
    return elements;
  }

  public ErlExpr tailOrNull() {
    return tailOrNull;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    String inline = inlineAsString();
    if (!ErlFormat.exceedsLineLimit(indent, inline)) {
      return List.of(inline);
    }
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, "["));
    for (int i = 0; i < elements.size(); i++) {
      String suffix = i < elements.size() - 1 ? "," : "";
      out.add(ErlFormat.prefixed(indent + 1, elements.get(i).asString() + suffix));
    }
    if (tailOrNull != null) {
      if (elements.isEmpty()) {
        out.add(ErlFormat.prefixed(indent + 1, tailOrNull.asString()));
      } else {
        String last = out.remove(out.size() - 1);
        if (last.endsWith(",")) {
          last = last.substring(0, last.length() - 1);
        }
        out.add(last);
        out.add(ErlFormat.prefixed(indent + 1, "| " + tailOrNull.asString()));
      }
    }
    out.add(ErlFormat.prefixed(indent, "]"));
    return out;
  }

  private String inlineAsString() {
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
    return sb.toString();
  }
}
