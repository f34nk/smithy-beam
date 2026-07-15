package io.smithy.beam.elixir;

import io.beam.dsl.elixir.DefstructField;
import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.TypeDef;
import io.beam.dsl.elixir.TypesModule;
import java.util.ArrayList;
import java.util.List;

final class ElixirBeamIrTypes {
  private static final int UNION_INLINE_LIMIT = 2;

  private ElixirBeamIrTypes() {}

  static TypesModule structNested(
      String name, Moduledoc moduledocOrNull, TypeDef typeDef, List<DefstructField> fields) {
    return new TypesModule(name, moduledocOrNull, typeDef, fields);
  }

  static String renderNestedBody(
      String shortName,
      Moduledoc moduledocOrNull,
      List<String> extraLines,
      List<Function> functions) {
    Module inner =
        new Module(
            shortName,
            moduledocOrNull,
            List.of(),
            List.of(),
            extraLines,
            List.of(),
            List.of(),
            List.of(),
            functions);
    return stripModuleWrapper(ElixirRenderer.render(inner));
  }

  static Module rootTypesModule(
      String moduleName,
      Moduledoc moduledoc,
      List<ElixirTypesEntry> entries,
      List<Function> functions) {
    List<String> moduleAttributes = new ArrayList<>();
    for (ElixirTypesEntry entry : entries) {
      switch (entry) {
        case ElixirTypesRootLine(String line) -> moduleAttributes.add(line);
        case ElixirTypesStructNested(TypesModule typesModule) ->
            moduleAttributes.addAll(inlineNestedModuleLines(typesModule));
        case ElixirTypesEmbeddedNested embedded ->
            moduleAttributes.addAll(embeddedNestedModuleLines(embedded));
        default -> {}
      }
    }
    return new Module(
        moduleName,
        moduledoc,
        List.of(),
        List.of(),
        moduleAttributes,
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static Module splitEmbeddedModule(String parentModuleName, ElixirTypesEmbeddedNested nested) {
    return new Module(
        parentModuleName + "." + nested.name(),
        nested.moduledocOrNull(),
        List.of(),
        List.of(),
        nested.extraLines(),
        List.of(),
        List.of(),
        List.of(),
        nested.functions());
  }

  static TypesModule splitStructModule(String parentModuleName, TypesModule typesModule) {
    return new TypesModule(
        parentModuleName + "." + typesModule.name(),
        typesModule.moduledocOrNull(),
        typesModule.typeDef(),
        typesModule.defstructFields());
  }

  static List<String> typedocRootLines(String text) {
    return docAttributeRootLines("@typedoc", text);
  }

  static List<String> docAttributeRootLines(String attribute, String text) {
    if (!text.contains("\n")) {
      return List.of(attribute + " \"" + escapeString(text) + "\"");
    }
    List<String> lines = new ArrayList<>();
    lines.add(attribute + " \"\"\"");
    for (String line : text.split("\n", -1)) {
      lines.add(line.isEmpty() ? "" : "    " + line);
    }
    lines.add("\"\"\"");
    return lines;
  }

  static List<String> typeAliasRootLines(String name, String body, List<String> commentLines) {
    List<String> lines = new ArrayList<>();
    for (String comment : commentLines) {
      lines.add("# " + comment);
    }
    lines.add("@type " + name + " :: " + body);
    return lines;
  }

  static List<String> unionTypeRootLines(String name, List<String> variants) {
    if (variants.size() <= UNION_INLINE_LIMIT) {
      return List.of("@type " + name + " :: " + String.join(" | ", variants));
    }
    List<String> lines = new ArrayList<>();
    lines.add("@type " + name + " ::");
    for (int i = 0; i < variants.size(); i++) {
      lines.add("    " + (i == 0 ? variants.get(i) : "| " + variants.get(i)));
    }
    return lines;
  }

  static TypeDef structureTypeDef(String name, List<String> fieldLines) {
    StringBuilder body = new StringBuilder();
    body.append("%__MODULE__{");
    for (int i = 0; i < fieldLines.size(); i++) {
      body.append("\n            ").append(fieldLines.get(i));
      if (i < fieldLines.size() - 1) {
        body.append(',');
      }
    }
    body.append("\n          }");
    return new TypeDef(name, body.toString());
  }

  static List<DefstructField> defstructFields(List<String> fieldNames) {
    return fieldNames.stream().map(DefstructField::field).toList();
  }

  static List<String> defexceptionLines(List<String> keywordFields) {
    if (keywordFields.isEmpty()) {
      return List.of("defexception []");
    }
    if (keywordFields.size() == 1) {
      return List.of("defexception " + keywordFields.get(0));
    }
    List<String> lines = new ArrayList<>();
    lines.add("defexception " + keywordFields.get(0) + ",");
    for (int i = 1; i < keywordFields.size(); i++) {
      String suffix = i < keywordFields.size() - 1 ? "," : "";
      lines.add("             " + keywordFields.get(i) + suffix);
    }
    return lines;
  }

  static boolean shouldSplitStruct(TypesModule typesModule, int defstructSplitThreshold) {
    return structNestedSizeEstimate(typesModule) > defstructSplitThreshold;
  }

  static boolean shouldSplitEmbedded(ElixirTypesEmbeddedNested nested, int enumSplitThreshold) {
    return isEnumEmbedded(nested) && enumEmbeddedSizeEstimate(nested) > enumSplitThreshold;
  }

  static int structNestedSizeEstimate(TypesModule typesModule) {
    int size = 0;
    for (DefstructField field : typesModule.defstructFields()) {
      if (field.nameOrNil() != null) {
        size += field.nameOrNil().length();
      }
    }
    String body = typesModule.typeDef().body();
    if (body.contains("{")) {
      String inner = body.substring(body.indexOf('{') + 1, body.lastIndexOf('}'));
      for (String line : inner.split("\n")) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
          continue;
        }
        if (trimmed.endsWith(",")) {
          trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        size += trimmed.length();
      }
    }
    return size;
  }

  static int enumEmbeddedSizeEstimate(ElixirTypesEmbeddedNested nested) {
    int size = nested.extraLines().stream().mapToInt(String::length).sum();
    for (Function function : nested.functions()) {
      size += ElixirRenderer.renderFunction(function).length();
    }
    return size;
  }

  private static boolean isEnumEmbedded(ElixirTypesEmbeddedNested nested) {
    if (nested.functions().isEmpty()) {
      return false;
    }
    boolean hasTypeAlias =
        nested.extraLines().stream().anyMatch(line -> line.startsWith("@type t ::"));
    boolean hasDefexception =
        nested.extraLines().stream().anyMatch(line -> line.startsWith("defexception"));
    return hasTypeAlias && !hasDefexception;
  }

  private static List<String> inlineNestedModuleLines(TypesModule typesModule) {
    return renderedModuleLines(ElixirRenderer.render(typesModule));
  }

  private static List<String> embeddedNestedModuleLines(ElixirTypesEmbeddedNested nested) {
    String rendered =
        ElixirRenderer.render(
            new Module(
                nested.name(),
                nested.moduledocOrNull(),
                List.of(),
                List.of(),
                nested.extraLines(),
                List.of(),
                List.of(),
                List.of(),
                nested.functions()));
    return renderedModuleLines(rendered);
  }

  private static List<String> renderedModuleLines(String rendered) {
    if (rendered.isEmpty()) {
      return List.of();
    }
    return List.of(rendered.stripTrailing().split("\n", -1));
  }

  private static String escapeString(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String stripModuleWrapper(String rendered) {
    int start = rendered.indexOf('\n');
    if (start < 0) {
      return rendered;
    }
    String body = rendered.substring(start + 1);
    if (body.endsWith("end\n")) {
      body = body.substring(0, body.length() - "end\n".length());
    } else if (body.endsWith("end")) {
      body = body.substring(0, body.length() - "end".length());
    }
    return body.stripTrailing();
  }
}
