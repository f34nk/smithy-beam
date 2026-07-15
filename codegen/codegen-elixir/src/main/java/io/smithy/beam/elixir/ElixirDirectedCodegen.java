package io.smithy.beam.elixir;

import io.beam.ir.elixir.Moduledoc;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.*;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.SparseTrait;

/**
 * DirectedCodegen implementation for the Elixir types generator.
 *
 * <p>Types are composed as beam-ir {@link io.beam.ir.elixir.Module} trees and emitted from {@link
 * #customizeAfterIntegrations} via {@link ElixirTypesEmission}. By default all types stay in one
 * file; when {@link BeamSettings#typesDefstructSplitThreshold} is lowered, only oversized structure
 * modules are written to separate files under the default {@code types/} directory.
 *
 * <p>Constraint traits do not narrow generated types; see {@link
 * io.smithy.beam.core.BeamConstraintPolicy}.
 */
final class ElixirDirectedCodegen
    implements DirectedCodegen<ElixirContext, BeamSettings, ElixirIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    ServiceShape service = directive.service();
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamElixirLayout layout = new BeamElixirLayout(settings, ns, service);
    String definitionFile = layout.typesModuleFile();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    return SymbolProvider.cache(
        new ElixirSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            moduleName,
            BeamCodegenKind.TYPES));
  }

  @Override
  public ElixirContext createContext(
      CreateContextDirective<BeamSettings, ElixirIntegration> directive) {
    ServiceShape service = directive.service();
    BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
    BeamProtocolCodegen protocolCodegen = null;
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamElixirLayout layout = new BeamElixirLayout(settings, ns, service);
    String definitionFile = layout.typesModuleFile();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    return new ElixirContext(
        directive.model(),
        directive.settings(),
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(), directive.symbolProvider(), ElixirWriter.factory(moduleName)),
        directive.integrations(),
        service,
        httpBindings,
        protocolCodegen,
        null,
        moduleName,
        definitionFile);
  }

  @Override
  public void customizeBeforeShapeGeneration(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    Model model = directive.model();
    SymbolProvider sp = directive.symbolProvider();
    Set<Shape> closure = new Walker(model).walkShapes(directive.service());
    Set<ShapeId> preambleAliasesEmitted = new LinkedHashSet<>();

    BeamDocumentation.forShape(directive.service())
        .ifPresentOrElse(
            doc -> ctx.addTypesModuledoc(Moduledoc.of(doc)),
            () -> {
              BeamElixirLayout layout =
                  new BeamElixirLayout(
                      ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
              String modelName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
              ctx.addTypesModuledoc(
                  Moduledoc.of(
                      "Type definitions for the "
                          + modelName
                          + " model.\n\nNamed after the model namespace per the baseline spec."));
            });

    writeScalarAliases(ctx, model, closure, sp, preambleAliasesEmitted);
    writeListAliases(ctx, model, closure, sp, preambleAliasesEmitted);
    writeMapAliases(ctx, model, closure, sp, preambleAliasesEmitted);

    assertPreambleAliasCoverage(closure, sp, preambleAliasesEmitted);
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

  static Set<ShapeId> expectedPreambleAliasShapeIds(
      Set<Shape> closure, SymbolProvider symbolProvider) {
    return closure.stream()
        .filter(ElixirDirectedCodegen::shouldEmitPreambleTypeAlias)
        .filter(shape -> !isBuiltinTypeSymbol(symbolProvider.toSymbol(shape)))
        .map(Shape::getId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static boolean isBuiltinTypeSymbol(Symbol symbol) {
    return symbol.getProperty("builtIn", Boolean.class).orElse(false);
  }

  private static void recordPreambleAlias(Shape shape, Set<ShapeId> emitted) {
    if (!emitted.add(shape.getId())) {
      assert false : "duplicate preamble alias for " + shape.getId();
    }
  }

  private static void assertPreambleAliasCoverage(
      Set<Shape> closure, SymbolProvider symbolProvider, Set<ShapeId> emitted) {
    Set<ShapeId> expected = expectedPreambleAliasShapeIds(closure, symbolProvider);
    for (ShapeId id : expected) {
      assert emitted.contains(id) : "missing preamble alias for " + id;
    }
    for (ShapeId id : emitted) {
      assert expected.contains(id) : "unexpected preamble alias for " + id;
    }
  }

  private static void addScalarTypeAlias(
      ElixirContext ctx, Shape shape, Symbol sym, List<String> docComments) {
    String baseType = sym.getProperty("baseType", String.class).orElse("any()");
    List<String> comments = new ArrayList<>(docComments);
    if (shape instanceof BigDecimalShape) {
      comments.add("Decimal.t()");
    } else if (shape instanceof BlobShape
        && sym.getProperty("streamingBlob", Boolean.class).orElse(false)) {
      comments.add("Streaming payload; framing deferred to protocol layer.");
    }
    ctx.addTypesRootLines(ElixirBeamIrTypes.typeAliasRootLines(sym.getName(), baseType, comments));
  }

  private static List<String> shapeDocComments(Shape shape) {
    return BeamDocumentation.forShape(shape)
        .map(ElixirDirectedCodegen::docToCommentLines)
        .orElse(List.of());
  }

  private static List<String> docToCommentLines(String doc) {
    return List.of(doc.split("\n", -1));
  }

  private void writeScalarAliases(
      ElixirContext ctx,
      Model model,
      Set<Shape> closure,
      SymbolProvider sp,
      Set<ShapeId> preambleAliasesEmitted) {
    writeElixirTypeAliases(ctx, model.getBlobShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getBooleanShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getStringShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getByteShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getShortShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getIntegerShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getLongShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getFloatShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getDoubleShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getBigIntegerShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getBigDecimalShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getTimestampShapes(), closure, sp, preambleAliasesEmitted);
    writeElixirTypeAliases(ctx, model.getDocumentShapes(), closure, sp, preambleAliasesEmitted);
  }

  private <S extends Shape> void writeElixirTypeAliases(
      ElixirContext ctx,
      Set<S> shapes,
      Set<Shape> closure,
      SymbolProvider sp,
      Set<ShapeId> preambleAliasesEmitted) {
    shapes.stream()
        .filter(closure::contains)
        .filter(ElixirDirectedCodegen::shouldEmitPreambleTypeAlias)
        .sorted(Comparator.comparing(s -> s.getId().getName()))
        .forEach(
            s -> {
              Symbol sym = sp.toSymbol(s);
              if (isBuiltinTypeSymbol(sym)) {
                return;
              }
              recordPreambleAlias(s, preambleAliasesEmitted);
              addScalarTypeAlias(ctx, s, sym, shapeDocComments(s));
            });
  }

  private void writeListAliases(
      ElixirContext ctx,
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
              String elementType = renderElixirType(ctx, memberSym);
              if (s.hasTrait(SparseTrait.ID)) {
                elementType = elementType + " | nil";
              }
              ctx.addTypesRootLines(
                  ElixirBeamIrTypes.typeAliasRootLines(
                      sym.getName(), "[" + elementType + "]", shapeDocComments(s)));
            });
  }

  private void writeMapAliases(
      ElixirContext ctx,
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
              Symbol sym = sp.toSymbol(s);
              Symbol keySym = sp.toSymbol(s.getKey());
              Symbol valueSym = sp.toSymbol(s.getValue());
              String keyType = renderElixirType(ctx, keySym);
              String valueType = renderElixirType(ctx, valueSym);
              if (s.hasTrait(SparseTrait.ID)) {
                valueType = valueType + " | nil";
              }
              ctx.addTypesRootLines(
                  ElixirBeamIrTypes.typeAliasRootLines(
                      sym.getName(),
                      "%{" + keyType + " => " + valueType + "}",
                      shapeDocComments(s)));
            });
  }

  static String renderElixirType(ElixirContext ctx, Symbol symbol) {
    boolean builtIn = symbol.getProperty("builtIn", Boolean.class).orElse(false);
    if (builtIn) {
      return symbol.getName();
    }
    String typeKind = symbol.getProperty("typeKind", String.class).orElse("alias");
    if ("module".equals(typeKind)) {
      return ctx.moduleName() + "." + symbol.getName() + ".t()";
    }
    return ctx.moduleName() + "." + symbol.getName() + "()";
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    // No action required for the types-only baseline.
  }

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    ServiceShape service = directive.context().service();
    if (BeamEndpointRuleSetEmitter.hasRuleSet(directive.model(), service)) {
      String json =
          BeamEndpointRuleSetEmitter.serializeRuleSetJson(directive.model(), service).orElseThrow();
      for (String line : ElixirTypesIr.endpointRuleSetEntries(json)) {
        directive.context().addTypesRootLine(line);
      }
      directive.context().addTypesFunction(ElixirTypesIr.endpointRuleSetFunction());
    }
    ElixirTypesEmission.emit(directive.context());
  }

  @Override
  public void generateService(GenerateServiceDirective<ElixirContext, BeamSettings> directive) {
    // Client/server passes own service emission.
  }

  @Override
  public void generateResource(GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
    // Client/server passes own resource emission.
  }

  @Override
  public void generateEnumShape(GenerateEnumDirective<ElixirContext, BeamSettings> directive) {
    EnumShape shape = directive.expectEnumShape();
    Symbol symbol = directive.symbolProvider().toSymbol(shape);
    directive
        .context()
        .addTypesEmbeddedNested(ElixirTypesNestedIr.buildEnumNestedModule(shape, symbol));
  }

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<ElixirContext, BeamSettings> directive) {
    IntEnumShape shape = directive.expectIntEnumShape();
    Symbol symbol = directive.symbolProvider().toSymbol(shape);
    directive
        .context()
        .addTypesEmbeddedNested(ElixirTypesNestedIr.buildIntEnumNestedModule(shape, symbol));
  }

  @Override
  public void generateUnion(GenerateUnionDirective<ElixirContext, BeamSettings> directive) {
    UnionShape shape = directive.shape();
    ElixirContext ctx = directive.context();
    SymbolProvider sp = directive.symbolProvider();
    Symbol symbol = sp.toSymbol(shape);

    List<String> variants =
        shape.members().stream()
            .map(
                m -> {
                  Symbol memberSym = sp.toSymbol(m);
                  String tag = ":" + memberSym.getProperty("unionTag", String.class).orElseThrow();
                  String memberType = renderElixirType(ctx, memberSym);
                  return "{" + tag + ", " + memberType + "}";
                })
            .collect(Collectors.toList());
    variants.add("{:unknown, String.t()}");

    BeamDocumentation.forShape(shape)
        .ifPresent(doc -> ctx.addTypesRootLines(ElixirBeamIrTypes.typedocRootLines(doc)));
    ctx.addTypesRootLines(ElixirBeamIrTypes.unionTypeRootLines(symbol.getName(), variants));
  }

  @Override
  public void generateStructure(GenerateStructureDirective<ElixirContext, BeamSettings> directive) {
    StructureShape shape = directive.shape();
    ElixirContext ctx = directive.context();
    SymbolProvider sp = directive.symbolProvider();
    NullableIndex nullableIndex = NullableIndex.of(directive.model());
    Symbol symbol = sp.toSymbol(shape);
    List<MemberShape> members = StreamSupport.stream(shape.members().spliterator(), false).toList();

    ctx.addTypesStructNested(
        ElixirTypesNestedIr.buildStructureNestedModule(
            shape, symbol, ctx, sp, nullableIndex, members));
  }

  @Override
  public void generateError(GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    StructureShape shape = directive.shape();
    String modName = ctx.symbolProvider().toSymbol(shape).getName();
    ErrorTrait errorTrait = shape.expectTrait(ErrorTrait.class);
    BeamRetryIndex.RetryInfo retryInfo = BeamRetryIndex.forError(shape).orElseThrow();

    ctx.addTypesRootLine("# Error shape: " + shape.getId() + " (" + errorTrait.getValue() + ")");
    ctx.addTypesEmbeddedNested(
        ElixirTypesNestedIr.buildErrorNestedModule(
            shape,
            modName,
            errorTrait,
            retryInfo.retryable(),
            retryInfo.throttling(),
            ctx.symbolProvider()));
  }
}
