package io.beam.lang.gleam;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class DefaultGleamRenderer implements Renderer {

  @Override
  public String render(Module module) {
    List<String> parts = new ArrayList<>();

    if (module.moduleDocOrNull() != null) {
      parts.add(renderDoc(module.moduleDocOrNull(), "////"));
    }

    if (!module.imports().isEmpty()) {
      parts.add(
          module.imports().stream().map(this::renderImport).collect(Collectors.joining("\n")));
    }

    for (TypeAlias alias : module.typeAliases()) {
      parts.add(render(alias).stripTrailing());
    }

    for (CustomType customType : module.customTypes()) {
      parts.add(render(customType).stripTrailing());
    }

    if (parts.isEmpty()) {
      return "\n";
    }
    return String.join("\n\n", parts) + "\n";
  }

  @Override
  public String render(TypeAlias typeAlias) {
    StringBuilder out = new StringBuilder();
    if (typeAlias.docOrNull() != null) {
      out.append(renderDoc(typeAlias.docOrNull(), "///")).append('\n');
    }
    if (typeAlias.pub()) {
      out.append("pub ");
    }
    out.append("type ")
        .append(typeAlias.name())
        .append(" = ")
        .append(typeAlias.definition())
        .append('\n');
    return out.toString();
  }

  @Override
  public String render(CustomType customType) {
    StringBuilder out = new StringBuilder();
    if (customType.docOrNull() != null) {
      out.append(renderDoc(customType.docOrNull(), "///")).append('\n');
    }
    if (customType.pub()) {
      out.append("pub ");
    }
    out.append("type ").append(customType.name()).append(" {\n");
    for (Variant variant : customType.variants()) {
      out.append("  ").append(renderVariant(variant)).append('\n');
    }
    out.append("}\n");
    return out.toString();
  }

  private String renderImport(Import importStmt) {
    if (importStmt.typeNames().isEmpty()) {
      return "import " + importStmt.modulePath();
    }
    String types =
        importStmt.typeNames().stream()
            .map(name -> "type " + name)
            .collect(Collectors.joining(", "));
    return "import " + importStmt.modulePath() + ".{" + types + "}";
  }

  private String renderVariant(Variant variant) {
    if (variant.fields().isEmpty()) {
      return variant.name();
    }
    String fields =
        variant.fields().stream().map(this::renderField).collect(Collectors.joining(", "));
    return variant.name() + "(" + fields + ")";
  }

  private String renderField(Field field) {
    if (field.labelOrNull() == null || field.labelOrNull().isEmpty()) {
      return field.typeExpression();
    }
    return field.labelOrNull() + ": " + field.typeExpression();
  }

  private static String renderDoc(DocComment doc, String prefix) {
    String text = doc.text() == null ? "" : doc.text();
    if (text.isEmpty()) {
      return prefix;
    }
    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      String line = lines[i];
      if (line.isEmpty()) {
        out.append(prefix);
      } else {
        out.append(prefix).append(' ').append(line);
      }
    }
    return out.toString();
  }
}
