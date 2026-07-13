package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamMemberNullability;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExComment;
import io.smithy.beam.ir.elixir.ExDefexception;
import io.smithy.beam.ir.elixir.ExDefstruct;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExNestedModule;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExTypeDef;
import io.smithy.beam.ir.elixir.ExTypedoc;
import io.smithy.beam.ir.elixir.ExTypesModule;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>Types are composed as {@link ExTypesModule} and emitted from {@link
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
            doc -> ctx.addTypesPreambleEntry(ExModuledoc.moduledoc(doc)),
            () -> {
              BeamElixirLayout layout =
                  new BeamElixirLayout(
                      ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
              String modelName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
              ctx.addTypesPreambleEntry(
                  ExModuledoc.moduledoc(
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

  private static ExTypeDef scalarTypeAlias(Shape shape, Symbol sym, List<ExComment> docPreamble) {
    String baseType = sym.getProperty("baseType", String.class).orElse("any()");
    List<ExComment> preamble = new ArrayList<>(docPreamble);
    if (shape instanceof BigDecimalShape) {
      preamble.add(ExComment.comment("Decimal.t()"));
    } else if (shape instanceof BlobShape
        && sym.getProperty("streamingBlob", Boolean.class).orElse(false)) {
      preamble.add(ExComment.comment("Streaming payload; framing deferred to protocol layer."));
    }
    return ExTypeDef.alias(sym.getName(), baseType, preamble);
  }

  private static List<ExComment> shapeDocComments(Shape shape) {
    return BeamDocumentation.forShape(shape)
        .map(ElixirDirectedCodegen::docToComments)
        .orElse(List.of());
  }

  private static List<ExComment> docToComments(String doc) {
    List<ExComment> comments = new ArrayList<>();
    for (String line : doc.split("\n", -1)) {
      comments.add(ExComment.comment(line));
    }
    return comments;
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
              ctx.addTypesEntry(scalarTypeAlias(s, sym, shapeDocComments(s)));
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
              ctx.addTypesEntry(
                  ExTypeDef.alias(sym.getName(), "[" + elementType + "]", shapeDocComments(s)));
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
              ctx.addTypesEntry(
                  ExTypeDef.alias(
                      sym.getName(),
                      "%{" + keyType + " => " + valueType + "}",
                      shapeDocComments(s)));
            });
  }

  private static String renderElixirType(ElixirContext ctx, Symbol symbol) {
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
      for (ExModuleEntry entry : ElixirTypesIr.endpointRuleSetEntries(json)) {
        directive.context().addTypesEntry(entry);
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
    directive.context().addTypesEntry(buildEnumNestedModule(shape, symbol));
  }

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<ElixirContext, BeamSettings> directive) {
    IntEnumShape shape = directive.expectIntEnumShape();
    Symbol symbol = directive.symbolProvider().toSymbol(shape);
    directive.context().addTypesEntry(buildIntEnumNestedModule(shape, symbol));
  }

  static ExNestedModule buildEnumNestedModule(EnumShape shape, Symbol symbol) {
    List<String> atoms = expectStringListProperty(symbol, "enumAtoms");
    String fromFunction = symbol.expectProperty("fromValueFunction", String.class);
    String toFunction = symbol.expectProperty("toValueFunction", String.class);
    String valuesFunction = symbol.expectProperty("valuesFunction", String.class);
    List<Map.Entry<String, String>> members = new ArrayList<>(shape.getEnumValues().entrySet());

    List<ExPreambleEntry> preamble = enumModuledoc(shape, true);
    List<ExModuleEntry> entries = new ArrayList<>();
    List<ExFunction> functions = new ArrayList<>();

    if (atoms.isEmpty()) {
      entries.add(ExTypeDef.alias("t", "{:unknown, String.t()}"));
      functions.add(
          ExFunction.functionWithSpec(
              "def",
              fromFunction,
              ExSpec.functionSpec(fromFunction, "String.t()", "t()"),
              List.of(
                  ExClause.inlineClause(
                      List.of(ExVarPattern.var("v")),
                      ExTuple.tuple(ExAtom.atom("unknown"), ExVar.var("v"))))));
      functions.add(
          ExFunction.functionWithSpec(
              "def",
              toFunction,
              ExSpec.functionSpec(toFunction, "t()", "String.t()"),
              List.of(
                  ExClause.inlineClause(
                      List.of(
                          ExTuplePattern.tuple(
                              ExAtomPattern.atom("unknown"), ExVarPattern.var("v"))),
                      ExVar.var("v")))));
      functions.add(
          ExFunction.functionWithSpec(
              "def",
              valuesFunction,
              ExSpec.functionSpec("values", "", "[t()]"),
              List.of(ExClause.inlineClause(List.of(), ExList.list()))));
      return ExNestedModule.nestedModule(symbol.getName(), preamble, entries, functions);
    }

    String atomVariants = atoms.stream().map(atom -> ":" + atom).collect(Collectors.joining(" | "));
    entries.add(ExTypeDef.alias("t", atomVariants + " | {:unknown, String.t()}"));

    List<ExClause> fromClauses = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      Map.Entry<String, String> entry = members.get(i);
      fromClauses.add(
          ExClause.inlineClause(
              List.of(ExStringPattern.string(entry.getValue())), ExAtom.atom(atoms.get(i))));
    }
    fromClauses.add(
        ExClause.blockClause(
            List.of(ExVarPattern.var("v")),
            List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
            ElixirEnumHelperIr.enumStringDecodeFallbackBody(fromFunction)));
    functions.add(
        ExFunction.functionWithSpec(
            "def",
            fromFunction,
            ExSpec.functionSpec(fromFunction, "String.t()", "t()"),
            fromClauses));

    List<ExClause> toClauses = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      Map.Entry<String, String> entry = members.get(i);
      toClauses.add(
          ExClause.inlineClause(
              List.of(ExAtomPattern.atom(atoms.get(i))), ExString.string(entry.getValue())));
    }
    toClauses.add(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(ExAtomPattern.atom("unknown"), ExVarPattern.var("v"))),
            ExVar.var("v")));
    functions.add(
        ExFunction.functionWithSpec(
            "def", toFunction, ExSpec.functionSpec(toFunction, "t()", "String.t()"), toClauses));

    ExList valuesList =
        ExList.list(
            atoms.stream().map(ExAtom::atom).toArray(io.smithy.beam.ir.elixir.ExExpr[]::new));
    functions.add(
        ExFunction.functionWithSpec(
            "def",
            valuesFunction,
            ExSpec.functionSpec("values", "", "[t()]"),
            List.of(ExClause.inlineClause(List.of(), valuesList))));

    return ExNestedModule.nestedModule(symbol.getName(), preamble, entries, functions);
  }

  static ExNestedModule buildIntEnumNestedModule(IntEnumShape shape, Symbol symbol) {
    List<String> atoms = expectStringListProperty(symbol, "enumAtoms");
    String fromFunction = symbol.expectProperty("fromValueFunction", String.class);
    String toFunction = symbol.expectProperty("toValueFunction", String.class);
    String valuesFunction = symbol.expectProperty("valuesFunction", String.class);
    List<Map.Entry<String, Integer>> members = new ArrayList<>(shape.getEnumValues().entrySet());

    List<ExPreambleEntry> preamble = enumModuledoc(shape, false);
    List<ExModuleEntry> entries = new ArrayList<>();
    List<ExFunction> functions = new ArrayList<>();

    if (atoms.isEmpty()) {
      entries.add(ExTypeDef.alias("t", "{:unknown, integer()}"));
      functions.add(
          ExFunction.functionWithSpec(
              "def",
              fromFunction,
              ExSpec.functionSpec(fromFunction, "integer()", "t()"),
              List.of(
                  ExClause.inlineClause(
                      List.of(ExVarPattern.var("v")),
                      ExTuple.tuple(ExAtom.atom("unknown"), ExVar.var("v"))))));
      functions.add(
          ExFunction.functionWithSpec(
              "def",
              toFunction,
              ExSpec.functionSpec(toFunction, "t()", "integer()"),
              List.of(
                  ExClause.inlineClause(
                      List.of(
                          ExTuplePattern.tuple(
                              ExAtomPattern.atom("unknown"), ExVarPattern.var("v"))),
                      ExVar.var("v")))));
      functions.add(
          ExFunction.functionWithSpec(
              "def",
              valuesFunction,
              ExSpec.functionSpec("values", "", "[t()]"),
              List.of(ExClause.inlineClause(List.of(), ExList.list()))));
      return ExNestedModule.nestedModule(symbol.getName(), preamble, entries, functions);
    }

    String atomVariants = atoms.stream().map(atom -> ":" + atom).collect(Collectors.joining(" | "));
    entries.add(ExTypeDef.alias("t", atomVariants + " | {:unknown, integer()}"));

    List<ExClause> fromClauses = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      Map.Entry<String, Integer> entry = members.get(i);
      fromClauses.add(
          ExClause.inlineClause(
              List.of(ExIntegerPattern.integer(entry.getValue())), ExAtom.atom(atoms.get(i))));
    }
    fromClauses.add(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("v")), ExTuple.tuple(ExAtom.atom("unknown"), ExVar.var("v"))));
    functions.add(
        ExFunction.functionWithSpec(
            "def",
            fromFunction,
            ExSpec.functionSpec(fromFunction, "integer()", "t()"),
            fromClauses));

    List<ExClause> toClauses = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      Map.Entry<String, Integer> entry = members.get(i);
      toClauses.add(
          ExClause.inlineClause(
              List.of(ExAtomPattern.atom(atoms.get(i))), ExInteger.integer(entry.getValue())));
    }
    toClauses.add(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(ExAtomPattern.atom("unknown"), ExVarPattern.var("v"))),
            ExVar.var("v")));
    functions.add(
        ExFunction.functionWithSpec(
            "def", toFunction, ExSpec.functionSpec(toFunction, "t()", "integer()"), toClauses));

    ExList valuesList =
        ExList.list(
            atoms.stream().map(ExAtom::atom).toArray(io.smithy.beam.ir.elixir.ExExpr[]::new));
    functions.add(
        ExFunction.functionWithSpec(
            "def",
            valuesFunction,
            ExSpec.functionSpec("values", "", "[t()]"),
            List.of(ExClause.inlineClause(List.of(), valuesList))));

    return ExNestedModule.nestedModule(symbol.getName(), preamble, entries, functions);
  }

  private static List<ExPreambleEntry> enumModuledoc(Shape shape, boolean stringEnum) {
    List<ExPreambleEntry> preamble = new ArrayList<>();
    BeamDocumentation.forShape(shape)
        .ifPresentOrElse(
            doc -> preamble.add(ExModuledoc.moduledoc(doc)),
            () ->
                preamble.add(
                    ExModuledoc.moduledoc(
                        stringEnum
                            ? "String enum. Unknown values are represented as {:unknown, String.t()}."
                            : "Integer enum. Unknown values are represented as {:unknown, integer()}.")));
    return preamble;
  }

  private static List<String> expectStringListProperty(Symbol symbol, String propertyName) {
    List<?> values = symbol.expectProperty(propertyName, List.class);
    return values.stream().map(String.class::cast).toList();
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

    BeamDocumentation.forShape(shape).ifPresent(doc -> ctx.addTypesEntry(ExTypedoc.typedoc(doc)));
    ctx.addTypesEntry(ExTypeDef.unionType(symbol.getName(), variants));
  }

  @Override
  public void generateStructure(GenerateStructureDirective<ElixirContext, BeamSettings> directive) {
    StructureShape shape = directive.shape();
    ElixirContext ctx = directive.context();
    SymbolProvider sp = directive.symbolProvider();
    NullableIndex nullableIndex = NullableIndex.of(directive.model());
    Symbol symbol = sp.toSymbol(shape);
    List<MemberShape> members = StreamSupport.stream(shape.members().spliterator(), false).toList();

    ctx.addTypesEntry(buildStructureNestedModule(shape, symbol, ctx, sp, nullableIndex, members));
  }

  static ExNestedModule buildStructureNestedModule(
      StructureShape shape,
      Symbol symbol,
      ElixirContext ctx,
      SymbolProvider sp,
      NullableIndex ni,
      List<MemberShape> members) {
    List<ExModuleEntry> entries = new ArrayList<>();
    List<String> fieldLines = new ArrayList<>();
    List<String> defstructFields = new ArrayList<>();
    for (MemberShape member : members) {
      Symbol memberSym = sp.toSymbol(member);
      String fieldName =
          memberSym
              .getProperty("fieldName", String.class)
              .orElse(BeamNameUtils.toSnakeCase(member.getMemberName()));
      String typeStr = renderElixirType(ctx, memberSym);
      if (BeamMemberNullability.isMemberNullable(ni, shape, member)) {
        typeStr = typeStr + " | nil";
      }
      fieldLines.add(fieldName + ": " + typeStr);
      defstructFields.add(":" + fieldName);
    }
    entries.add(ExTypeDef.structureType("t", fieldLines));
    entries.add(ExDefstruct.defstruct(defstructFields));
    List<ExPreambleEntry> preamble = new ArrayList<>();
    BeamDocumentation.elixirStructureModuledoc(shape)
        .ifPresentOrElse(
            doc -> preamble.add(ExModuledoc.moduledoc(doc)),
            () -> preamble.add(ExModuledoc.moduledoc("structure " + shape.getId().getName())));
    return ExNestedModule.nestedModule(symbol.getName(), preamble, entries, List.of());
  }

  @Override
  public void generateError(GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    StructureShape shape = directive.shape();
    String modName = ctx.symbolProvider().toSymbol(shape).getName();
    ErrorTrait errorTrait = shape.expectTrait(ErrorTrait.class);
    BeamRetryIndex.RetryInfo retryInfo = BeamRetryIndex.forError(shape).orElseThrow();

    ctx.addTypesEntry(
        ExComment.comment("Error shape: " + shape.getId() + " (" + errorTrait.getValue() + ")"));
    ctx.addTypesEntry(
        buildErrorNestedModule(
            shape,
            modName,
            errorTrait,
            retryInfo.retryable(),
            retryInfo.throttling(),
            ctx.symbolProvider()));
  }

  static ExNestedModule buildErrorNestedModule(
      StructureShape shape,
      String modName,
      ErrorTrait errorTrait,
      boolean isRetryable,
      boolean isThrottling,
      SymbolProvider sp) {
    List<ExPreambleEntry> preamble = new ArrayList<>();
    BeamDocumentation.forShape(shape)
        .ifPresentOrElse(
            doc -> preamble.add(ExModuledoc.moduledoc(doc)),
            () ->
                preamble.add(
                    ExModuledoc.moduledoc(
                        "Error from "
                            + shape.getId()
                            + " (fault: "
                            + errorTrait.getValue()
                            + ", retryable: "
                            + isRetryable
                            + ").")));

    List<String> exceptionFields = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      Symbol memberSym = sp.toSymbol(member);
      String fieldName =
          memberSym
              .getProperty("fieldName", String.class)
              .orElse(BeamNameUtils.toSnakeCase(member.getMemberName()));
      exceptionFields.add(fieldName + ": nil");
    }
    exceptionFields.add("__beam_error_kind: :" + errorTrait.getValue());

    List<ExModuleEntry> entries = List.of(ExDefexception.defexception(exceptionFields));
    List<ExFunction> functions =
        List.of(
            ExFunction.defFunction(
                "retryable",
                List.of(
                    ExClause.inlineClause(
                        List.of(ExStructPattern.struct("__MODULE__", List.of())),
                        ExAtom.atom(isRetryable ? "true" : "false")))),
            ExFunction.defFunction(
                "throttling",
                List.of(
                    ExClause.inlineClause(
                        List.of(ExStructPattern.struct("__MODULE__", List.of())),
                        ExAtom.atom(isThrottling ? "true" : "false")))),
            ExFunction.defFunctionWithImpl(
                "message",
                List.of(
                    ExClause.inlineClause(
                        List.of(ExVarPattern.var("e")),
                        ExCallLocal.callLocal("inspect", ExVar.var("e"))))));

    return ExNestedModule.nestedModule(modName, preamble, entries, functions);
  }
}
