package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExTypeDef implements ExModuleEntry {
  static final int UNION_INLINE_LIMIT = 2;

  private final String name;
  private final String body;
  private final List<ExComment> preamble;
  private final List<String> variants;
  private final List<String> structureFieldLinesOrNull;

  private ExTypeDef(
      String name,
      String body,
      List<ExComment> preamble,
      List<String> variants,
      List<String> structureFieldLinesOrNull) {
    this.name = name;
    this.body = body;
    this.preamble = List.copyOf(preamble);
    this.variants = List.copyOf(variants);
    this.structureFieldLinesOrNull = structureFieldLinesOrNull;
  }

  public static ExTypeDef alias(String name, String body, List<ExComment> preamble) {
    return new ExTypeDef(name, body, preamble, List.of(), null);
  }

  public static ExTypeDef alias(String name, String body) {
    return alias(name, body, List.of());
  }

  public static ExTypeDef unionType(String name, List<String> variants) {
    return new ExTypeDef(name, String.join(" | ", variants), List.of(), variants, null);
  }

  public static ExTypeDef structureType(String name, List<String> fieldLines) {
    return new ExTypeDef(name, "", List.of(), List.of(), List.copyOf(fieldLines));
  }

  public String name() {
    return name;
  }

  public String body() {
    return body;
  }

  public List<ExComment> preamble() {
    return preamble;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    for (ExComment comment : preamble) {
      out.addAll(comment.lines(indent));
    }
    out.addAll(typeBodyLines(indent));
    return out;
  }

  private List<String> typeBodyLines(int indent) {
    if (structureFieldLinesOrNull != null) {
      return structureTypeLines(indent);
    }
    if (variants.size() <= UNION_INLINE_LIMIT) {
      return List.of(IrObject.indent(indent) + "@type " + name + " :: " + body);
    }
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "@type " + name + " ::");
    for (int i = 0; i < variants.size(); i++) {
      if (i == 0) {
        out.add(IrObject.indent(indent + 4) + variants.get(i));
      } else {
        out.add(IrObject.indent(indent + 4) + "| " + variants.get(i));
      }
    }
    return out;
  }

  private List<String> structureTypeLines(int indent) {
    List<String> out = new ArrayList<>();
    out.add(IrObject.indent(indent) + "@type " + name + " :: %__MODULE__{");
    int fieldIndent = indent + 4;
    for (int i = 0; i < structureFieldLinesOrNull.size(); i++) {
      String suffix = (i < structureFieldLinesOrNull.size() - 1) ? "," : "";
      out.add(IrObject.indent(fieldIndent) + structureFieldLinesOrNull.get(i) + suffix);
    }
    out.add(IrObject.indent(indent) + "}");
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
