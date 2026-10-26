package io.smithy.beam.gleam;

import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.TypeAlias;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
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
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.TimestampShape;
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
  public void generateEnumShape(GenerateEnumDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateUnion(GenerateUnionDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateStructure(
      GenerateStructureDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateError(GenerateErrorDirective<GleamContext, BeamSettings> directive) {}

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

  private static void maybeAddImportForBaseType(GleamContext ctx, String baseType) {
    if (baseType.startsWith("timestamp.")) {
      ctx.typesBuilder().addImport(Import.of("gleam/time/timestamp"));
    } else if (baseType.startsWith("bigdecimal.")) {
      ctx.typesBuilder().addImport(Import.of("bigdecimal"));
    } else if (baseType.startsWith("dynamic.")) {
      ctx.typesBuilder().addImport(Import.of("gleam/dynamic"));
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
