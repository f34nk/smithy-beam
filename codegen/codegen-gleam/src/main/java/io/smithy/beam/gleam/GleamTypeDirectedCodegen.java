package io.smithy.beam.gleam;

import io.beam.lang.gleam.CustomType;
import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Field;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.TypeAlias;
import io.beam.lang.gleam.Variant;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamMemberNullability;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.SparseTrait;

/** DirectedCodegen implementation for the Gleam types generator. */
final class GleamTypeDirectedCodegen
    implements DirectedCodegen<GleamContext, BeamSettings, GleamIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    ServiceShape service = directive.service();
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamGleamLayout layout = new BeamGleamLayout(settings, ns, service);
    String definitionFile = layout.typesModuleFile();
    return SymbolProvider.cache(
        new GleamSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            layout.typesModuleName(),
            BeamCodegenKind.TYPES));
  }

  @Override
  public GleamContext createContext(
      CreateContextDirective<BeamSettings, GleamIntegration> directive) {
    ServiceShape service = directive.service();
    BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamGleamLayout layout = new BeamGleamLayout(settings, ns, service);
    String definitionFile = layout.typesModuleFile();
    String moduleName = layout.typesModuleName();
    return GleamContext.forTypes(
        directive.model(),
        directive.settings(),
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(), directive.symbolProvider(), GleamWriter.factory()),
        directive.integrations(),
        service,
        httpBindings,
        moduleName,
        definitionFile);
  }

  @Override
  public void customizeBeforeShapeGeneration(
      CustomizeDirective<GleamContext, BeamSettings> directive) {
    GleamContext ctx = directive.context();
    Model model = directive.model();
    SymbolProvider sp = directive.symbolProvider();
    Set<Shape> closure = new Walker(model).walkShapes(directive.service());
    Set<ShapeId> preambleAliasesEmitted = new LinkedHashSet<>();

    BeamDocumentation.forShape(directive.service())
        .ifPresentOrElse(
            doc -> ctx.typesBuilder().setModuleDoc(DocComment.of(doc)),
            () ->
                ctx.typesBuilder()
                    .setModuleDoc(
                        DocComment.of(
                            "Type definitions for the " + ctx.moduleName() + " model.")));

    writeScalarAliases(ctx, model, closure, sp, preambleAliasesEmitted);
    writeListAliases(ctx, model, closure, sp, preambleAliasesEmitted);
    writeMapAliases(ctx, model, closure, sp, preambleAliasesEmitted);
    verifyPreambleAliasCoverage(closure, sp, preambleAliasesEmitted);
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<GleamContext, BeamSettings> directive) {
    GleamTypesEmission.emit(directive.context());
  }

  @Override
  public void generateService(GenerateServiceDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateResource(GenerateResourceDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateEnumShape(GenerateEnumDirective<GleamContext, BeamSettings> directive) {
    EnumShape shape = directive.expectEnumShape();
    emitEnumLikeCustomType(directive.context(), directive.symbolProvider(), shape, "String");
  }

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<GleamContext, BeamSettings> directive) {
    IntEnumShape shape = directive.expectIntEnumShape();
    emitEnumLikeCustomType(directive.context(), directive.symbolProvider(), shape, "Int");
  }

  @Override
  public void generateUnion(GenerateUnionDirective<GleamContext, BeamSettings> directive) {
    UnionShape shape = directive.shape();
    GleamContext ctx = directive.context();
    SymbolProvider sp = directive.symbolProvider();
    Symbol symbol = sp.toSymbol(shape);

    List<Variant> variants = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      Symbol memberSym = sp.toSymbol(member);
      String ctor = memberSym.getProperty("constructorName", String.class).orElseThrow();
      String payloadType = renderGleamType(memberSym);
      maybeAddImportForTypeExpression(ctx, payloadType);
      variants.add(Variant.of(ctor, List.of(Field.positional(payloadType))));
    }
    String unknownCtor =
        symbol
            .getProperty("unknownConstructor", String.class)
            .orElseThrow(
                () -> new CodegenException("missing unknown constructor for " + shape.getId()));
    variants.add(Variant.of(unknownCtor, List.of(Field.positional("String"))));

    DocComment doc = BeamDocumentation.forShape(shape).map(DocComment::of).orElse(null);
    ctx.typesBuilder()
        .addCustomType(
            doc == null
                ? CustomType.pub(symbol.getName(), variants)
                : CustomType.pub(symbol.getName(), variants, doc));
  }

  @Override
  public void generateStructure(
      GenerateStructureDirective<GleamContext, BeamSettings> directive) {
    emitStructureCustomType(
        directive.context(),
        directive.symbolProvider(),
        directive.model(),
        directive.shape());
  }

  @Override
  public void generateError(GenerateErrorDirective<GleamContext, BeamSettings> directive) {
    emitStructureCustomType(
        directive.context(),
        directive.symbolProvider(),
        directive.model(),
        directive.shape());
  }

  static boolean isPreludeShape(Shape shape) {
    return shape.getId().getNamespace().equals("smithy.api");
  }

  static boolean receivesPreambleTypeAlias(Shape shape) {
    if (shape instanceof EnumShape || shape instanceof IntEnumShape) {
      return false;
    }
    return shape instanceof BlobShape
        || shape instanceof BooleanShape
        || shape instanceof StringShape
        || shape instanceof ByteShape
        || shape instanceof ShortShape
        || shape instanceof IntegerShape
        || shape instanceof LongShape
        || shape instanceof FloatShape
        || shape instanceof DoubleShape
        || shape instanceof BigIntegerShape
        || shape instanceof BigDecimalShape
        || shape instanceof TimestampShape
        || shape instanceof DocumentShape
        || shape instanceof ListShape
        || shape instanceof MapShape;
  }

  static boolean shouldEmitPreambleTypeAlias(Shape shape) {
    return receivesPreambleTypeAlias(shape) && !isPreludeShape(shape);
  }

  static String renderGleamType(Symbol symbol) {
    boolean builtIn = symbol.getProperty("builtIn", Boolean.class).orElse(false);
    if (builtIn) {
      return symbol.getName();
    }
    return symbol.getName();
  }

  private static void writeScalarAliases(
      GleamContext ctx,
      Model model,
      Set<Shape> closure,
      SymbolProvider sp,
      Set<ShapeId> preambleAliasesEmitted) {
    writeAliases(ctx, model.getBlobShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getBooleanShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getStringShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getByteShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getShortShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getIntegerShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getLongShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getFloatShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getDoubleShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getBigIntegerShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getBigDecimalShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getTimestampShapes(), closure, sp, preambleAliasesEmitted);
    writeAliases(ctx, model.getDocumentShapes(), closure, sp, preambleAliasesEmitted);
  }

  private static <S extends Shape> void writeAliases(
      GleamContext ctx,
      Set<S> shapes,
      Set<Shape> closure,
      SymbolProvider sp,
      Set<ShapeId> preambleAliasesEmitted) {
    shapes.stream()
        .filter(closure::contains)
        .filter(GleamTypeDirectedCodegen::shouldEmitPreambleTypeAlias)
        .sorted(Comparator.comparing(s -> s.getId().getName()))
        .forEach(
            s -> {
              Symbol sym = sp.toSymbol(s);
              if (isBuiltinTypeSymbol(sym)) {
                return;
              }
              recordPreambleAlias(s, preambleAliasesEmitted);
              maybeAddImportForBaseType(ctx, sym.getProperty("baseType", String.class).orElse(""));
              DocComment doc =
                  BeamDocumentation.forShape(s).map(DocComment::of).orElse(null);
              String baseType = sym.getProperty("baseType", String.class).orElseThrow();
              ctx.typesBuilder()
                  .addTypeAlias(
                      doc == null
                          ? TypeAlias.pub(sym.getName(), baseType)
                          : TypeAlias.pub(sym.getName(), baseType, doc));
            });
  }

  private static void writeListAliases(
      GleamContext ctx,
      Model model,
      Set<Shape> closure,
      SymbolProvider sp,
      Set<ShapeId> preambleAliasesEmitted) {
    model.getListShapes().stream()
        .filter(closure::contains)
        .sorted(Comparator.comparing(s -> s.getId().getName()))
        .forEach(
            s -> {
              recordPreambleAlias(s, preambleAliasesEmitted);
              Symbol sym = sp.toSymbol(s);
              Symbol memberSym = sp.toSymbol(s.getMember());
              String elementType = renderGleamType(memberSym);
              if (s.hasTrait(SparseTrait.ID)) {
                ctx.typesBuilder().addImport(Import.type("gleam/option", "Option"));
                elementType = "Option(" + elementType + ")";
              }
              DocComment doc =
                  BeamDocumentation.forShape(s).map(DocComment::of).orElse(null);
              ctx.typesBuilder()
                  .addTypeAlias(
                      doc == null
                          ? TypeAlias.pub(sym.getName(), "List(" + elementType + ")")
                          : TypeAlias.pub(sym.getName(), "List(" + elementType + ")", doc));
            });
  }

  private static void writeMapAliases(
      GleamContext ctx,
      Model model,
      Set<Shape> closure,
      SymbolProvider sp,
      Set<ShapeId> preambleAliasesEmitted) {
    model.getMapShapes().stream()
        .filter(closure::contains)
        .sorted(Comparator.comparing(s -> s.getId().getName()))
        .forEach(
            s -> {
              recordPreambleAlias(s, preambleAliasesEmitted);
              ctx.typesBuilder().addImport(Import.type("gleam/dict", "Dict"));
              Symbol sym = sp.toSymbol(s);
              String keyType = renderGleamType(sp.toSymbol(s.getKey()));
              String valueType = renderGleamType(sp.toSymbol(s.getValue()));
              if (s.hasTrait(SparseTrait.ID)) {
                ctx.typesBuilder().addImport(Import.type("gleam/option", "Option"));
                valueType = "Option(" + valueType + ")";
              }
              DocComment doc =
                  BeamDocumentation.forShape(s).map(DocComment::of).orElse(null);
              String definition = "Dict(" + keyType + ", " + valueType + ")";
              ctx.typesBuilder()
                  .addTypeAlias(
                      doc == null
                          ? TypeAlias.pub(sym.getName(), definition)
                          : TypeAlias.pub(sym.getName(), definition, doc));
            });
  }

  private static void emitEnumLikeCustomType(
      GleamContext ctx, SymbolProvider sp, Shape shape, String unknownPayloadType) {
    Symbol symbol = sp.toSymbol(shape);
    @SuppressWarnings("unchecked")
    Map<String, String> byMember =
        symbol.getProperty("enumConstructorByMember", Map.class).orElseGet(Map::of);
    List<Variant> variants = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      String ctor = byMember.get(member.getMemberName());
      if (ctor == null) {
        throw new CodegenException(
            "missing enum constructor for " + shape.getId() + "#" + member.getMemberName());
      }
      variants.add(Variant.of(ctor));
    }
    String unknownCtor =
        symbol
            .getProperty("unknownConstructor", String.class)
            .orElseThrow(
                () -> new CodegenException("missing unknown constructor for " + shape.getId()));
    variants.add(Variant.of(unknownCtor, List.of(Field.positional(unknownPayloadType))));

    DocComment doc = BeamDocumentation.forShape(shape).map(DocComment::of).orElse(null);
    ctx.typesBuilder()
        .addCustomType(
            doc == null
                ? CustomType.pub(symbol.getName(), variants)
                : CustomType.pub(symbol.getName(), variants, doc));
  }

  private static void emitStructureCustomType(
      GleamContext ctx, SymbolProvider sp, Model model, StructureShape shape) {
    Symbol symbol = sp.toSymbol(shape);
    String ctor =
        symbol.getProperty("constructorName", String.class).orElse(symbol.getName());
    NullableIndex nullableIndex = NullableIndex.of(model);
    List<MemberShape> members =
        StreamSupport.stream(shape.members().spliterator(), false).toList();
    List<Field> fields = new ArrayList<>();
    for (MemberShape member : members) {
      Symbol memberSym = sp.toSymbol(member);
      String fieldName = memberSym.getProperty("fieldName", String.class).orElseThrow();
      String memberType = renderGleamType(memberSym);
      if (BeamMemberNullability.isMemberNullable(nullableIndex, shape, member)) {
        ctx.typesBuilder().addImport(Import.type("gleam/option", "Option"));
        memberType = "Option(" + memberType + ")";
      }
      maybeAddImportForTypeExpression(ctx, memberType);
      fields.add(Field.labelled(fieldName, memberType));
    }
    Variant variant = fields.isEmpty() ? Variant.of(ctor) : Variant.of(ctor, fields);
    DocComment doc = BeamDocumentation.forShape(shape).map(DocComment::of).orElse(null);
    ctx.typesBuilder()
        .addCustomType(
            doc == null
                ? CustomType.pub(symbol.getName(), List.of(variant))
                : CustomType.pub(symbol.getName(), List.of(variant), doc));
  }

  private static void maybeAddImportForBaseType(GleamContext ctx, String baseType) {
    maybeAddImportForTypeExpression(ctx, baseType);
  }

  private static void maybeAddImportForTypeExpression(GleamContext ctx, String typeExpression) {
    if (typeExpression.contains("timestamp.")) {
      ctx.typesBuilder().addImport(Import.of("gleam/time/timestamp"));
    }
    if (typeExpression.contains("bigdecimal.")) {
      ctx.typesBuilder().addImport(Import.of("bigdecimal"));
    }
    if (typeExpression.contains("dynamic.")) {
      ctx.typesBuilder().addImport(Import.of("gleam/dynamic"));
    }
    if (typeExpression.contains("Dict(")) {
      ctx.typesBuilder().addImport(Import.type("gleam/dict", "Dict"));
    }
    if (typeExpression.contains("Option(")) {
      ctx.typesBuilder().addImport(Import.type("gleam/option", "Option"));
    }
  }

  private static boolean isBuiltinTypeSymbol(Symbol symbol) {
    return symbol.getProperty("builtIn", Boolean.class).orElse(false);
  }

  private static void recordPreambleAlias(Shape shape, Set<ShapeId> emitted) {
    if (!emitted.add(shape.getId())) {
      throw new CodegenException("duplicate preamble alias for " + shape.getId());
    }
  }

  private static void verifyPreambleAliasCoverage(
      Set<Shape> closure, SymbolProvider symbolProvider, Set<ShapeId> emitted) {
    Set<ShapeId> expected =
        closure.stream()
            .filter(GleamTypeDirectedCodegen::shouldEmitPreambleTypeAlias)
            .filter(shape -> !isBuiltinTypeSymbol(symbolProvider.toSymbol(shape)))
            .map(Shape::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    for (ShapeId id : expected) {
      if (!emitted.contains(id)) {
        throw new CodegenException("missing preamble alias for " + id);
      }
    }
    for (ShapeId id : emitted) {
      if (!expected.contains(id)) {
        throw new CodegenException("unexpected preamble alias for " + id);
      }
    }
  }
}
