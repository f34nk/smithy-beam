package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirBuiltinTypes;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamNameIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamScalarTypeAliases;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.StreamingTrait;

final class ElixirSymbolProvider implements SymbolProvider, ShapeVisitor<Symbol> {

  private final BeamSettings settings;
  private final Model model;
  private final ServiceShape service;
  private final String definitionFile;
  private final BeamCodegenKind kind;
  private final String moduleNamespace;
  private final ReservedWords typeNameEscaper;
  private final ReservedWords moduleNameEscaper;
  private final ReservedWords fieldNameEscaper;
  private final ReservedWords atomEscaper;
  private final ReservedWords functionNameEscaper;
  private final Map<ShapeId, String> typeNames;
  private final Map<ShapeId, String> moduleNames;
  private final Map<ShapeId, String> fieldNames;
  private final Map<ShapeId, String> unionTagNames;
  private final Map<ShapeId, Map<String, String>> enumAtomNames;
  private final Map<ShapeId, String> serviceFunctionNames;

  ElixirSymbolProvider(
      BeamSettings settings,
      Model model,
      ServiceShape service,
      String definitionFile,
      String moduleNamespace,
      BeamCodegenKind kind) {
    this.settings = settings;
    this.model = model;
    this.service = service;
    this.definitionFile = definitionFile;
    this.moduleNamespace = moduleNamespace;
    this.kind = kind;
    this.typeNameEscaper = elixirReservedWords();
    this.moduleNameEscaper = elixirReservedWords();
    this.fieldNameEscaper = elixirReservedWords();
    this.atomEscaper = elixirReservedWords();
    this.functionNameEscaper = elixirFunctionReservedWords();
    BeamNameIndex nameIndex =
        BeamNameIndex.of(
            model,
            service,
            BeamNameIndex.Escapers.withModuleNames(
                typeNameEscaper,
                fieldNameEscaper,
                atomEscaper,
                functionNameEscaper,
                moduleNameEscaper));
    this.typeNames = nameIndex.typeNames();
    this.moduleNames = toUpperCamelModuleNames(nameIndex.moduleNames());
    this.fieldNames = nameIndex.fieldNames();
    this.unionTagNames = nameIndex.unionTagNames();
    this.enumAtomNames = nameIndex.enumAtomNames();
    this.serviceFunctionNames = nameIndex.serviceFunctionNames();
  }

  @Override
  public Symbol toSymbol(Shape shape) {
    return shape.accept(this);
  }

  // ── Prelude scalar shapes ────────────────────────────────────────────────

  @Override
  public Symbol blobShape(BlobShape shape) {
    if (isPrelude(shape)) {
      return builtin("binary()");
    }
    Symbol base = namedScalar(shape, "binary()");
    if (shape.hasTrait(StreamingTrait.ID)) {
      return base.toBuilder().putProperty("streamingBlob", true).build();
    }
    return base;
  }

  @Override
  public Symbol booleanShape(BooleanShape shape) {
    return isPrelude(shape) ? builtin("boolean()") : namedScalar(shape, "boolean()");
  }

  @Override
  public Symbol stringShape(StringShape shape) {
    return isPrelude(shape) ? builtin("String.t()") : namedScalar(shape, "String.t()");
  }

  @Override
  public Symbol byteShape(ByteShape shape) {
    return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
  }

  @Override
  public Symbol shortShape(ShortShape shape) {
    return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
  }

  @Override
  public Symbol integerShape(IntegerShape shape) {
    return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
  }

  @Override
  public Symbol longShape(LongShape shape) {
    return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
  }

  @Override
  public Symbol floatShape(FloatShape shape) {
    return isPrelude(shape) ? builtin("float()") : namedScalar(shape, "float()");
  }

  @Override
  public Symbol doubleShape(DoubleShape shape) {
    return isPrelude(shape) ? builtin("float()") : namedScalar(shape, "float()");
  }

  @Override
  public Symbol bigIntegerShape(BigIntegerShape shape) {
    return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
  }

  @Override
  public Symbol bigDecimalShape(BigDecimalShape shape) {
    return isPrelude(shape) ? builtin("Decimal.t()") : namedScalar(shape, "Decimal.t()");
  }

  @Override
  public Symbol timestampShape(TimestampShape shape) {
    // Type-surface representation for a Smithy instant. Wire timestamp
    // format handling belongs to future protocol serializers.
    return isPrelude(shape) ? builtin("DateTime.t()") : namedScalar(shape, "DateTime.t()");
  }

  @Override
  public Symbol documentShape(DocumentShape shape) {
    return isPrelude(shape) ? builtin("any()") : namedScalar(shape, "any()");
  }

  // ── Aggregate shapes ─────────────────────────────────────────────────────

  @Override
  public Symbol listShape(ListShape shape) {
    return namedAlias(shape);
  }

  @Override
  public Symbol mapShape(MapShape shape) {
    return namedAlias(shape);
  }

  @Override
  public Symbol unionShape(UnionShape shape) {
    return namedAlias(shape);
  }

  @Override
  public Symbol structureShape(StructureShape shape) {
    return namedModule(shape);
  }

  @Override
  public Symbol memberShape(MemberShape shape) {
    Symbol.Builder builder = toSymbol(model.expectShape(shape.getTarget())).toBuilder();
    Optional.ofNullable(fieldNames.get(shape.getId()))
        .ifPresent(name -> builder.putProperty("fieldName", name));
    Optional.ofNullable(unionTagNames.get(shape.getId()))
        .ifPresent(name -> builder.putProperty("unionTag", name));
    return builder.build();
  }

  @Override
  public Symbol enumShape(EnumShape shape) {
    return namedModule(shape).toBuilder()
        .putProperty("enumAtoms", new ArrayList<>(enumAtomNames.get(shape.getId()).values()))
        .putProperty("enumAtomByMember", enumAtomNames.get(shape.getId()))
        .putProperty("fromValueFunction", toFunctionName("from_string"))
        .putProperty("toValueFunction", toFunctionName("to_string"))
        .putProperty("valuesFunction", toFunctionName("values"))
        .build();
  }

  @Override
  public Symbol intEnumShape(IntEnumShape shape) {
    return namedModule(shape).toBuilder()
        .putProperty("enumAtoms", new ArrayList<>(enumAtomNames.get(shape.getId()).values()))
        .putProperty("enumAtomByMember", enumAtomNames.get(shape.getId()))
        .putProperty("fromValueFunction", toFunctionName("from_integer"))
        .putProperty("toValueFunction", toFunctionName("to_integer"))
        .putProperty("valuesFunction", toFunctionName("values"))
        .build();
  }

  // ── Service / Operation / Resource ───────────────────────────────────────

  @Override
  public Symbol serviceShape(ServiceShape shape) {
    BeamElixirLayout layout = new BeamElixirLayout(settings, shape.getId().getNamespace(), service);
    String name =
        switch (kind) {
          case TYPES -> ElixirSymbolProvider.toModuleName(layout.typesModuleName());
          case CLIENT -> ElixirSymbolProvider.toModuleName(layout.clientModuleName());
          case SERVER -> ElixirSymbolProvider.toModuleName(layout.serverModuleName());
        };
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(kind == BeamCodegenKind.TYPES ? "" : definitionFile)
        .putProperty("builtIn", false)
        .putProperty("beamKind", kind.name())
        .build();
  }

  @Override
  public Symbol operationShape(OperationShape shape) {
    return serviceScopedFunctionSymbol(shape);
  }

  @Override
  public Symbol resourceShape(ResourceShape shape) {
    return serviceScopedFunctionSymbol(shape);
  }

  private Symbol serviceScopedFunctionSymbol(Shape shape) {
    String name =
        serviceFunctionNames.getOrDefault(
            shape.getId(),
            functionNameEscaper.escape(
                BeamNameUtils.toSnakeCase(shape.getId().getName(service))));
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(kind == BeamCodegenKind.TYPES ? "" : definitionFile)
        .putProperty("builtIn", false)
        .putProperty("beamKind", kind.name())
        .build();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private Symbol builtin(String name) {
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .putProperty("builtIn", true)
        .build();
  }

  private Symbol namedScalar(Shape shape, String baseType) {
    String name = toTypeName(shape);
    if (BeamScalarTypeAliases.isRedundant(name, baseType)
        || BeamElixirBuiltinTypes.shadowsBuiltinTypeName(name)) {
      return builtin(baseType);
    }
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "alias")
        .putProperty("baseType", baseType)
        .build();
  }

  private Symbol namedAlias(Shape shape) {
    String name = toTypeName(shape);
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "alias")
        .build();
  }

  private Symbol namedModule(Shape shape) {
    String name = toModuleName(shape);
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "module")
        .build();
  }

  private boolean isPrelude(Shape shape) {
    return shape.getId().getNamespace().equals("smithy.api");
  }

  private String memberBaseName(MemberShape member) {
    ShapeId id = member.getId();
    return id.getMember().orElseGet(() -> id.getName(service));
  }

  String toTypeName(Shape shape) {
    return typeNames.getOrDefault(
        shape.getId(),
        typeNameEscaper.escape(BeamNameUtils.toSnakeCase(shape.getId().getName(service))));
  }

  String toModuleName(Shape shape) {
    return moduleNames.getOrDefault(
        shape.getId(),
        toModuleNameFromSnake(
            moduleNameEscaper.escape(
                BeamNameUtils.toSnakeCase(shape.getId().getName(service)))));
  }

  String toFieldName(MemberShape member) {
    return fieldNames.getOrDefault(
        member.getId(),
        fieldNameEscaper.escape(BeamNameUtils.toSnakeCase(memberBaseName(member))));
  }

  String toUnionTagName(MemberShape member) {
    return unionTagNames.getOrDefault(
        member.getId(), atomEscaper.escape(BeamNameUtils.toSnakeCase(memberBaseName(member))));
  }

  String toFunctionName(String functionName) {
    return functionNameEscaper.escape(BeamNameUtils.toSnakeCase(functionName));
  }

  List<String> toEnumAtomNames(EnumShape shape) {
    return new ArrayList<>(enumAtomNames.get(shape.getId()).values());
  }

  List<String> toEnumAtomNames(IntEnumShape shape) {
    return new ArrayList<>(enumAtomNames.get(shape.getId()).values());
  }

  String toEnumAtomName(Shape enumShape, String memberName) {
    return enumAtomNames.get(enumShape.getId()).get(memberName);
  }

  /** Converts the last namespace segment to UpperCamelCase module name. */
  static String toModuleName(String snakeName) {
    String escaped = elixirReservedWords().escape(BeamNameUtils.toSnakeCase(snakeName));
    return toModuleNameFromSnake(escaped);
  }

  private static String toModuleNameFromSnake(String escaped) {
    StringBuilder sb = new StringBuilder();
    for (String part : escaped.split("_")) {
      if (!part.isEmpty()) {
        sb.append(Character.toUpperCase(part.charAt(0)));
        sb.append(part.substring(1));
      }
    }
    return sb.toString();
  }

  private static Map<ShapeId, String> toUpperCamelModuleNames(Map<ShapeId, String> snakeNames) {
    Map<ShapeId, String> result = new HashMap<>();
    snakeNames.forEach((id, name) -> result.put(id, toModuleNameFromSnake(name)));
    return Map.copyOf(result);
  }

  private static ReservedWords elixirReservedWords() {
    return new ReservedWordsBuilder()
        .put("after", "after_")
        .put("begin", "begin_")
        .put("case", "case_")
        .put("catch", "catch_")
        .put("do", "do_")
        .put("else", "else_")
        .put("end", "end_")
        .put("fn", "fn_")
        .put("for", "for_")
        .put("if", "if_")
        .put("receive", "receive_")
        .put("rescue", "rescue_")
        .put("try", "try_")
        .put("when", "when_")
        .put("and", "and_")
        .put("or", "or_")
        .build();
  }

  private static ReservedWords elixirFunctionReservedWords() {
    return new ReservedWordsBuilder()
        .put("after", "after_")
        .put("begin", "begin_")
        .put("case", "case_")
        .put("catch", "catch_")
        .put("do", "do_")
        .put("else", "else_")
        .put("end", "end_")
        .put("fn", "fn_")
        .put("for", "for_")
        .put("if", "if_")
        .put("receive", "receive_")
        .put("rescue", "rescue_")
        .put("try", "try_")
        .put("when", "when_")
        .put("def", "def_")
        .put("defmodule", "defmodule_")
        .put("import", "import_")
        .put("alias", "alias_")
        .put("require", "require_")
        .put("use", "use_")
        .build();
  }
}
