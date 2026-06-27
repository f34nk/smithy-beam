package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlTuple implements ErlExpr {
  private final List<ErlExpr> elements;

  public ErlTuple(List<ErlExpr> elements) {
    this.elements = List.copyOf(elements);
  }

  public static ErlTuple tuple(ErlExpr... elements) {
    return new ErlTuple(List.of(elements));
  }

  public List<ErlExpr> elements() {
    return elements;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }

  @Override
  public List<String> lines(int indent) {
    if (elements.size() == 2 && elements.get(1) instanceof ErlMap map) {
      String inline = inlineAsString();
      if (ErlFormat.exceedsLineLimit(indent, inline) || map.lines(indent + 1).size() > 1) {
        List<String> out = new ArrayList<>();
        out.add(ErlFormat.prefixed(indent, "{" + elements.get(0).asString() + ", #{"));
        List<ErlMapEntry> entries = map.entries();
        for (int i = 0; i < entries.size(); i++) {
          String suffix = i < entries.size() - 1 ? "," : "";
          out.add(ErlFormat.prefixed(indent + 1, entries.get(i).asString() + suffix));
        }
        out.add(ErlFormat.prefixed(indent, "}}"));
        return out;
      }
    }
    if (elements.size() == 2
        && elements.get(0).lines().size() == 1
        && elements.get(1) instanceof ErlRecord record
        && !record.fields().isEmpty()) {
      List<String> out = new ArrayList<>();
      out.add(
          ErlFormat.prefixed(
              indent, "{" + elements.get(0).asString() + ", #" + record.name() + "{"));
      List<ErlRecordField> fields = record.fields();
      for (int i = 0; i < fields.size(); i++) {
        String suffix = i < fields.size() - 1 ? "," : "";
        out.add(
            ErlFormat.prefixed(indent + 1, fields.get(i).name())
                + " = "
                + fields.get(i).value().asString()
                + suffix);
      }
      out.add(ErlFormat.prefixed(indent, "}}"));
      return out;
    }
    if (elements.size() == 2
        && elements.get(1) instanceof ErlTuple nested
        && nested.elements().size() > 1
        && ErlFormat.exceedsLineLimit(indent, inlineAsString())) {
      List<String> out = new ArrayList<>();
      out.add(ErlFormat.prefixed(indent, "{" + elements.get(0).asString() + ", {"));
      StringBuilder inner = new StringBuilder();
      for (int i = 0; i < nested.elements().size(); i++) {
        if (i > 0) {
          inner.append(", ");
        }
        inner.append(nested.elements().get(i).asString());
      }
      out.add(ErlFormat.prefixed(indent + 1, inner.toString()));
      out.add(ErlFormat.prefixed(indent, "}}"));
      return out;
    }
    String inline = inlineAsString();
    if (!ErlFormat.exceedsLineLimit(indent, inline)) {
      return List.of(inline);
    }
    if (elements.size() == 2 && elements.get(1) instanceof ErlBinaryTemplate binary) {
      List<String> out = new ArrayList<>();
      out.add(ErlFormat.prefixed(indent, "{" + elements.get(0).asString() + ", <<"));
      out.add(ErlFormat.prefixed(indent + 1, binaryInner(binary)));
      out.add(ErlFormat.prefixed(indent, ">>}"));
      return out;
    }
    List<String> out = new ArrayList<>();
    out.add(ErlFormat.prefixed(indent, "{"));
    for (int i = 0; i < elements.size(); i++) {
      String suffix = i < elements.size() - 1 ? "," : "";
      out.add(ErlFormat.prefixed(indent + 1, elements.get(i).asString() + suffix));
    }
    out.add(ErlFormat.prefixed(indent, "}"));
    return out;
  }

  private static String binaryInner(ErlBinaryTemplate binary) {
    StringBuilder sb = new StringBuilder();
    List<ErlBinarySegment> segments = binary.segments();
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      if (segments.get(i) instanceof ErlBinaryText text) {
        sb.append(text.asString());
      } else {
        sb.append(((ErlBinaryExpr) segments.get(i)).asString());
      }
    }
    return sb.toString();
  }

  private String inlineAsString() {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(elements.get(i).asString());
    }
    sb.append('}');
    return sb.toString();
  }
}
