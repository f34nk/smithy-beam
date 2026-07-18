package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamNameIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamScalarTypeAliases;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
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

final class ErlangSymbolProvider implements SymbolProvider, ShapeVisitor<Symbol> {

  private final BeamSettings settings;
  private final Model model;
  private final ServiceShape service;
  private final String definitionFile;
  private final BeamCodegenKind kind;
  private final ReservedWords typeNameEscaper;
  private final ReservedWords fieldNameEscaper;
  private final ReservedWords atomEscaper;
  private final ReservedWords functionNameEscaper;
  private final Map<ShapeId, String> typeNames;
  private final Map<ShapeId, String> fieldNames;
  private final Map<ShapeId, String> unionTagNames;
  private final Map<ShapeId, Map<String, String>> enumAtomNames;
  private final Map<ShapeId, String> serviceFunctionNames;

  ErlangSymbolProvider(
      BeamSettings settings,
      Model model,
      ServiceShape service,
      String definitionFile,
      BeamCodegenKind kind) {
    this.settings = settings;
    this.model = model;
    this.service = service;
    this.definitionFile = definitionFile;
    this.kind = kind;
    this.typeNameEscaper = erlangKeywordReservedWords();
    this.fieldNameEscaper = erlangKeywordReservedWords();
    this.atomEscaper = erlangKeywordReservedWords();
    this.functionNameEscaper =
        ReservedWords.compose(erlangKeywordReservedWords(), erlangFunctionExportShadows());
    BeamNameIndex nameIndex =
        BeamNameIndex.of(
            model,
            service,
            BeamNameIndex.Escapers.of(
                typeNameEscaper, fieldNameEscaper, atomEscaper, functionNameEscaper));
    this.typeNames = nameIndex.typeNames();
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
    return isPrelude(shape) ? builtin("binary()") : namedScalar(shape, "binary()");
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
    // term() because there is no standard big-decimal type in OTP; decimal:decimal() via optional
    // lib
    return isPrelude(shape) ? builtin("term()") : namedScalar(shape, "term()");
  }

  @Override
  public Symbol timestampShape(TimestampShape shape) {
    // Type-surface representation for a Smithy instant. Wire timestamp
    // format handling belongs to future protocol serializers.
    return isPrelude(shape)
        ? builtin("erlang:timestamp()")
        : namedScalar(shape, "erlang:timestamp()");
  }

  @Override
  public Symbol documentShape(DocumentShape shape) {
    return isPrelude(shape) ? builtin("term()") : namedScalar(shape, "term()");
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

  // ── Enum shapes ──────────────────────────────────────────────────────────

  @Override
  public Symbol enumShape(EnumShape shape) {
    return namedModule(shape).toBuilder()
        .putProperty("enumAtoms", new ArrayList<>(enumAtomNames.get(shape.getId()).values()))
        .putProperty("enumAtomByMember", enumAtomNames.get(shape.getId()))
        .build();
  }

  @Override
  public Symbol intEnumShape(IntEnumShape shape) {
    return namedModule(shape).toBuilder()
        .putProperty("enumAtoms", new ArrayList<>(enumAtomNames.get(shape.getId()).values()))
        .putProperty("enumAtomByMember", enumAtomNames.get(shape.getId()))
        .build();
  }

  // ── Service / Operation / Resource (not types) ───────────────────────────

  @Override
  public Symbol serviceShape(ServiceShape shape) {
    BeamErlangLayout layout = new BeamErlangLayout(settings, shape.getId().getNamespace(), service);
    String module =
        switch (kind) {
          case TYPES -> layout.typesModuleName();
          case CLIENT -> layout.clientModuleName();
          case SERVER -> layout.serverModuleName();
        };
    return Symbol.builder()
        .name(module)
        .definitionFile(kind == BeamCodegenKind.TYPES ? "" : definitionFile)
        .putProperty("builtIn", false)
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

  /**
   * Symbol for an operation or resource: service-relative snake_case name suitable for Erlang
   * function atoms, with definition file only on client and server passes.
   */
  private Symbol serviceScopedFunctionSymbol(Shape shape) {
    String name =
        serviceFunctionNames.getOrDefault(
            shape.getId(),
            functionNameEscaper.escape(
                BeamNameUtils.toSnakeCase(shape.getId().getName(service))));
    return Symbol.builder()
        .name(name)
        .namespace(service.getId().getNamespace(), ".")
        .definitionFile(kind == BeamCodegenKind.TYPES ? "" : definitionFile)
        .putProperty("builtIn", false)
        .putProperty("beamKind", kind.name())
        .build();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private Symbol builtin(String name) {
    return Symbol.builder().name(name).putProperty("builtIn", true).build();
  }

  /**
   * Named scalar alias: the symbol points to the definition file so that the DirectedCodegen can
   * write "-type basic_foo() :: underlying()." for it. The "baseType" property carries the
   * underlying Erlang type for generation.
   */
  private Symbol namedScalar(Shape shape, String baseType) {
    String name = toTypeName(shape);
    String typeName = name + "()";
    if (BeamScalarTypeAliases.isRedundant(typeName, baseType)) {
      return builtin(baseType);
    }
    return Symbol.builder()
        .name(typeName)
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "alias")
        .putProperty("baseType", baseType)
        .build();
  }

  private Symbol namedAlias(Shape shape) {
    String name = toTypeName(shape);
    return Symbol.builder()
        .name(name + "()")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "alias")
        .build();
  }

  private Symbol namedModule(Shape shape) {
    String name = toTypeName(shape);
    return Symbol.builder()
        .name(name + "()")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "module")
        .build();
  }

  private boolean isPrelude(Shape shape) {
    return shape.getId().getNamespace().equals("smithy.api");
  }

  String toTypeName(Shape shape) {
    return typeNames.getOrDefault(
        shape.getId(),
        typeNameEscaper.escape(BeamNameUtils.toSnakeCase(shape.getId().getName(service))));
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

  private String memberBaseName(MemberShape member) {
    ShapeId id = member.getId();
    return id.getMember().orElseGet(() -> id.getName(service));
  }

  /** Erlang reserved words for type, field, and function identifiers (language keywords). */
  private static ReservedWords erlangKeywordReservedWords() {
    return new ReservedWordsBuilder()
        .put("after", "after_")
        .put("begin", "begin_")
        .put("case", "case_")
        .put("catch", "catch_")
        .put("end", "end_")
        .put("fun", "fun_")
        .put("if", "if_")
        .put("of", "of_")
        .put("receive", "receive_")
        .put("try", "try_")
        .put("when", "when_")
        .put("and", "and_")
        .put("or", "or_")
        .build();
  }

  /**
   * Names that collide with common attributes or BIF-style identifiers when used as exported
   * function names; composed after keywords on the function escaper only.
   */
  private static ReservedWords erlangFunctionExportShadows() {
    return new ReservedWordsBuilder()
        .put("module", "module_")
        .put("export", "export_")
        .put("record", "record_")
        .build();
  }
}
