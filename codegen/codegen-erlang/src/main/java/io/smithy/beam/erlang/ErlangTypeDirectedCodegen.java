package io.smithy.beam.erlang;

import io.beam.dsl.erlang.ErlangRenderer;
import io.beam.dsl.erlang.Header;
import io.beam.dsl.erlang.HeaderBlankLine;
import io.beam.dsl.erlang.HeaderComment;
import io.beam.dsl.erlang.HeaderEntry;
import io.beam.dsl.erlang.HeaderRecordEntry;
import io.beam.dsl.erlang.HeaderTypeAliasEntry;
import io.beam.dsl.erlang.RecordDef;
import io.beam.dsl.erlang.TypeAlias;
import io.beam.dsl.erlang.TypedField;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamDocumentation.DocTarget;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamMemberNullability;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
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
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EnumValueTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.SparseTrait;

/**
 * DirectedCodegen implementation for the Erlang types generator.
 *
 * <p>Constraint traits do not narrow Dialyzer types; see {@link
 * io.smithy.beam.core.BeamConstraintPolicy}.
 *
 * <p>CodegenDirector calls methods in this order: 1. customizeBeforeShapeGeneration -- file header
 * + scalar/list/map type aliases 2. generate* methods in topological order (enums, unions,
 * structures) 3. customizeBeforeIntegrations 4. integration.customize() calls 5.
 * customizeAfterIntegrations 6. flushWriters
 *
 * <p>generateService is a stub reserved for client/server generation. Resource helpers are emitted
 * by client/server DirectedCodegen classes.
 */
final class ErlangTypeDirectedCodegen
    implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

  // ── Factory methods ──────────────────────────────────────────────────────

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    ServiceShape service = directive.service();
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamErlangLayout layout = new BeamErlangLayout(settings, ns, service);
    String definitionFile = layout.typesHeaderFile();
    return SymbolProvider.cache(
        new ErlangSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            BeamCodegenKind.TYPES));
  }

  @Override
  public ErlangContext createContext(
      CreateContextDirective<BeamSettings, ErlangIntegration> directive) {
    ServiceShape service = directive.service();
    BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
    BeamProtocolCodegen protocolCodegen = null;
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamErlangLayout layout = new BeamErlangLayout(settings, ns, service);
    String definitionFile = layout.typesHeaderFile();
    String moduleName = layout.typesModuleName();
    return new ErlangContext(
        directive.model(),
        directive.settings(),
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(), directive.symbolProvider(), ErlangWriter.factory()),
        directive.integrations(),
        service,
        httpBindings,
        protocolCodegen,
        null,
        moduleName,
        definitionFile);
  }

  // ── Customization hooks ──────────────────────────────────────────────────

  /**
   * Runs before any generate* method. Writes the file header comment and scalar/list/map type
   * aliases by iterating named shapes in the service closure.
   */
  @Override
  public void customizeBeforeShapeGeneration(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    Model model = directive.model();
    Set<Shape> closure = new Walker(model).walkShapes(directive.service());
    Set<ShapeId> preambleAliasesEmitted = new LinkedHashSet<>();

    ctx.writerDelegator()
        .useFileWriter(
            ctx.definitionFile(),
            writer -> {
              writer.pushGeneratedDocumentationSection();
              BeamDocumentation.forShape(directive.service())
                  .ifPresentOrElse(
                      doc -> BeamDocumentation.writeErlangDoc(writer, doc),
                      () ->
                          writer.write(
                              "$L",
                              ErlangRenderer.render(
                                  Header.of(
                                      List.of(
                                          "Record and type definitions for the "
                                              + ctx.moduleName()
                                              + " model.",
                                          ""),
                                      List.of(),
                                      List.of()))));
              writer.popState();

              // Write named scalar type aliases in declaration order:
              // blob, boolean, string, byte, short, integer, long, float, double,
              // bigInteger, bigDecimal, timestamp, document.
              writeScalarAliases(
                  writer, model, closure, directive.symbolProvider(), preambleAliasesEmitted);

              // DirectedCodegen has no generateList or generateMap callback, so the
              // BEAM type-file aliases for list and map shapes must be written here.
              writeListAliases(
                  writer, model, closure, directive.symbolProvider(), preambleAliasesEmitted);
              writeMapAliases(
                  writer, model, closure, directive.symbolProvider(), preambleAliasesEmitted);

              assertPreambleAliasCoverage(
                  closure, directive.symbolProvider(), preambleAliasesEmitted);
            });
  }

  private static boolean isPreludeShape(Shape shape) {
    return shape.getId().getNamespace().equals("smithy.api");
  }

  /**
   * Returns true when a closure shape receives its {@code -type} alias from the preamble pass
   * rather than a {@code generate*} callback (enums, unions, and structures are excluded).
   */
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

  /**
   * Shape ids that must receive exactly one preamble {@code -type} alias for the given closure.
   * Scalars whose alias name equals the underlying built-in type are excluded.
   */
  static Set<ShapeId> expectedPreambleAliasShapeIds(
      Set<Shape> closure, SymbolProvider symbolProvider) {
    return closure.stream()
        .filter(ErlangTypeDirectedCodegen::shouldEmitPreambleTypeAlias)
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

  private void writeScalarAliases(
      ErlangWriter writer,
      Model model,
      Set<Shape> closure,
      SymbolProvider symbolProvider,
      Set<ShapeId> preambleAliasesEmitted) {

    // Iterate shape types in a defined order matching the baseline output.
    writeShapeTypeAliases(
        writer, model.getBlobShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getBooleanShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getStringShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getByteShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getShortShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getIntegerShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getLongShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getFloatShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getDoubleShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getBigIntegerShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getBigDecimalShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getTimestampShapes(), closure, symbolProvider, preambleAliasesEmitted);
    writeShapeTypeAliases(
        writer, model.getDocumentShapes(), closure, symbolProvider, preambleAliasesEmitted);
  }

  private <S extends Shape> void writeShapeTypeAliases(
      ErlangWriter writer,
      java.util.Set<S> shapes,
      Set<Shape> closure,
      SymbolProvider symbolProvider,
      Set<ShapeId> preambleAliasesEmitted) {
    shapes.stream()
        .filter(closure::contains)
        .filter(ErlangTypeDirectedCodegen::shouldEmitPreambleTypeAlias)
        .sorted(java.util.Comparator.comparing(s -> s.getId().getName()))
        .forEach(
            s -> {
              Symbol sym = symbolProvider.toSymbol(s);
              if (isBuiltinTypeSymbol(sym)) {
                return;
              }
              recordPreambleAlias(s, preambleAliasesEmitted);
              List<String> doc = shapeDocPreamble(s);
              writer.write("$L", renderTypeAlias(scalarTypeAlias(s, sym, doc)));
            });
  }

  private static TypeAlias scalarTypeAlias(Shape shape, Symbol sym, List<String> docPreamble) {
    String baseType = sym.getProperty("baseType", String.class).orElse("term()");
    List<String> preamble = new ArrayList<>(docPreamble);
    if (shape instanceof BigDecimalShape) {
      preamble.add("decimal:decimal()");
    } else if (shape instanceof BlobShape
        && sym.getProperty("streamingBlob", Boolean.class).orElse(false)) {
      preamble.add("streaming payload; framing deferred to protocol layer");
    }
    return TypeAlias.of(sym.getName(), baseType, preamble);
  }

  private static String renderTypeAlias(TypeAlias typeAlias) {
    return ErlangRenderer.render(
        Header.ofEntries(List.of(new HeaderTypeAliasEntry(typeAlias)), false));
  }

  private static List<String> shapeDocPreamble(Shape shape) {
    return BeamDocumentation.forShape(shape)
        .map(
            doc -> {
              List<String> comments = new ArrayList<>();
              if (!doc.contains("\n")) {
                comments.add("@doc " + doc);
              } else {
                comments.add("@doc");
                for (String line : doc.split("\n", -1)) {
                  comments.add(line);
                }
              }
              return comments;
            })
        .orElseGet(ArrayList::new);
  }

  private void writeListAliases(
      ErlangWriter writer,
      Model model,
      Set<Shape> closure,
      SymbolProvider symbolProvider,
      Set<ShapeId> preambleAliasesEmitted) {
    model.getListShapes().stream()
        .filter(closure::contains)
        .sorted(java.util.Comparator.comparing(s -> s.getId().getName()))
        .forEach(
            s -> {
              recordPreambleAlias(s, preambleAliasesEmitted);
              Symbol sym = symbolProvider.toSymbol(s);
              Symbol memberSym = symbolProvider.toSymbol(s.getMember());
              String elementType = renderErlangType(memberSym);
              if (s.hasTrait(SparseTrait.ID)) {
                elementType = elementType + " | undefined";
              }
              TypeAlias def =
                  TypeAlias.of(sym.getName(), "[" + elementType + "]", shapeDocPreamble(s));
              writer.write("$L", renderTypeAlias(def));
            });
  }

  private void writeMapAliases(
      ErlangWriter writer,
      Model model,
      Set<Shape> closure,
      SymbolProvider symbolProvider,
      Set<ShapeId> preambleAliasesEmitted) {
    model.getMapShapes().stream()
        .filter(closure::contains)
        .sorted(java.util.Comparator.comparing(s -> s.getId().getName()))
        .forEach(
            s -> {
              recordPreambleAlias(s, preambleAliasesEmitted);
              Symbol sym = symbolProvider.toSymbol(s);
              Symbol keySym = symbolProvider.toSymbol(s.getKey());
              Symbol valueSym = symbolProvider.toSymbol(s.getValue());
              String valueType = renderErlangType(valueSym);
              if (s.hasTrait(SparseTrait.ID)) {
                valueType = valueType + " | undefined";
              }
              TypeAlias def =
                  TypeAlias.of(
                      sym.getName(),
                      "#{" + renderErlangType(keySym) + " => " + valueType + "}",
                      shapeDocPreamble(s));
              writer.write("$L", renderTypeAlias(def));
            });
  }

  /**
   * Resolves a symbol to an Erlang type reference inside generated {@code -type} bodies. Built-in
   * shapes use the symbol name. Other shapes honor {@code typeKind} on the symbol ({@code alias}
   * for lists, maps, unions, and named scalars; {@code module} for structures, enums, and int
   * enums). All named service types share one header file, so both kinds currently render as the
   * type alias name from {@link Symbol#getName()}.
   */
  private static String renderErlangType(Symbol symbol) {
    boolean builtIn = symbol.getProperty("builtIn", Boolean.class).orElse(false);
    if (builtIn) {
      return symbol.getName();
    }
    String typeKind = symbol.getProperty("typeKind", String.class).orElse("alias");
    if ("module".equals(typeKind)) {
      return symbol.getName();
    }
    return symbol.getName();
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    // No action required for the types-only baseline.
  }

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    ServiceShape service = ctx.service();
    if (!BeamEndpointRuleSetEmitter.hasRuleSet(directive.model(), service)) {
      return;
    }
    String ruleSetMap =
        BeamEndpointRuleSetEmitter.serializeRuleSetErlangMap(directive.model(), service)
            .orElseThrow();
    String ns = service.getId().getNamespace();
    BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), ns, service);
    ctx.writerDelegator()
        .useFileWriter(
            layout.typesHeaderFile(),
            writer ->
                writer.write(
                    "$L",
                    ErlangRenderer.render(
                        Header.ofEntries(
                            ErlangTypesIr.endpointRuleSetEntries(ruleSetMap), false))));
  }

  // ── Service / Resource / Operation stubs ─────────────────────────────────
  // Reserved for future client/server generation. The types-only plugin does
  // not generate service, resource, or operation code.

  /**
   * Types pass: service clients and servers are emitted by {@link ErlangClientDirectedCodegen} and
   * {@link ErlangServerDirectedCodegen}.
   */
  @Override
  public void generateService(GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
    // Client/server passes own service emission.
  }

  /** Types pass: resource helpers are emitted by client/server DirectedCodegen classes. */
  @Override
  public void generateResource(GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
    // Client/server passes own resource emission.
  }

  // ── Type generation ──────────────────────────────────────────────────────

  /**
   * Generates the -type declaration for a Smithy enum shape.
   *
   * <p>Output format: -type basic_status() :: active | inactive | pending | {unknown, binary()}.
   */
  @Override
  public void generateEnumShape(GenerateEnumDirective<ErlangContext, BeamSettings> directive) {
    EnumShape shape = directive.expectEnumShape();
    ErlangContext ctx = directive.context();
    SymbolProvider sp = directive.symbolProvider();
    Symbol symbol = sp.toSymbol(shape);
    String definitionFile = symbol.getDefinitionFile();

    List<String> atoms = symbol.getProperty("enumAtoms", List.class).orElseThrow();
    @SuppressWarnings("unchecked")
    Map<String, String> atomByMember =
        symbol.getProperty("enumAtomByMember", Map.class).orElseThrow();

    if (atoms.isEmpty()) {
      ctx.writerDelegator()
          .useFileWriter(
              definitionFile,
              writer -> {
                writer.pushGeneratedDocumentationSection();
                BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
                writer.write(
                    "$L",
                    enumTypeDeclaration(
                        symbol,
                        List.of(),
                        shapeDocPreamble(shape),
                        shape.getId(),
                        atomByMember,
                        List.copyOf(shape.members())));
                writer.popState();
              });
      return;
    }

    ctx.writerDelegator()
        .useFileWriter(
            definitionFile,
            writer -> {
              writer.pushGeneratedDocumentationSection();
              BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
              writer.write(
                  "$L",
                  enumTypeDeclaration(
                      symbol,
                      atoms,
                      shapeDocPreamble(shape),
                      shape.getId(),
                      atomByMember,
                      List.copyOf(shape.members())));
              writer.popState();
            });
  }

  private static String enumTypeDeclaration(
      Symbol symbol,
      List<String> atoms,
      List<String> shapeDoc,
      ShapeId shapeId,
      Map<String, String> atomByMember,
      List<MemberShape> members) {
    List<HeaderEntry> entries = new ArrayList<>();
    if (atoms.isEmpty()) {
      entries.add(
          new HeaderTypeAliasEntry(
              TypeAlias.of(symbol.getName(), "{unknown, binary()}", shapeDoc)));
    } else {
      String variants = String.join(" | ", atoms) + " | {unknown, binary()}";
      entries.add(new HeaderTypeAliasEntry(TypeAlias.of(symbol.getName(), variants, shapeDoc)));
      entries.add(new HeaderComment("Wire values for " + shapeId));
      for (MemberShape member : members) {
        String wireValue =
            member
                .getTrait(EnumValueTrait.class)
                .flatMap(EnumValueTrait::getStringValue)
                .orElse(member.getMemberName());
        String atom = atomByMember.get(member.getMemberName());
        entries.add(new HeaderComment("  " + atom + " -> <<\"" + wireValue + ">>"));
      }
    }
    return ErlangRenderer.render(Header.ofEntries(entries, false));
  }

  /**
   * Generates the -type declaration for a Smithy intEnum shape.
   *
   * <p>Output format: -type basic_priority() :: low | medium | high | {unknown, integer()}.
   */
  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<ErlangContext, BeamSettings> directive) {
    IntEnumShape shape = directive.expectIntEnumShape();
    ErlangContext ctx = directive.context();
    SymbolProvider sp = directive.symbolProvider();
    Symbol symbol = sp.toSymbol(shape);
    String definitionFile = symbol.getDefinitionFile();

    List<String> atoms = symbol.getProperty("enumAtoms", List.class).orElseThrow();

    if (atoms.isEmpty()) {
      ctx.writerDelegator()
          .useFileWriter(
              definitionFile,
              writer -> {
                writer.pushGeneratedDocumentationSection();
                BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
                writer.write(
                    "$L",
                    renderTypeAlias(
                        TypeAlias.of(
                            symbol.getName(), "{unknown, integer()}", shapeDocPreamble(shape))));
                writer.popState();
              });
      return;
    }

    ctx.writerDelegator()
        .useFileWriter(
            definitionFile,
            writer -> {
              writer.pushGeneratedDocumentationSection();
              BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
              String variants = String.join(" | ", atoms) + " | {unknown, integer()}";
              writer.write(
                  "$L",
                  renderTypeAlias(
                      TypeAlias.of(symbol.getName(), variants, shapeDocPreamble(shape))));
              writer.popState();
            });
  }

  /**
   * Generates the -type declaration for a Smithy union shape.
   *
   * <p>Output format: -type basic_union() :: {text, basic_string()} | {number, basic_integer()} |
   * {unknown, binary()}.
   */
  @Override
  public void generateUnion(GenerateUnionDirective<ErlangContext, BeamSettings> directive) {
    UnionShape shape = directive.shape();
    ErlangContext ctx = directive.context();
    SymbolProvider sp = directive.symbolProvider();
    Symbol symbol = sp.toSymbol(shape);
    String definitionFile = symbol.getDefinitionFile();

    ctx.writerDelegator()
        .useFileWriter(
            definitionFile,
            writer -> {
              writer.pushGeneratedDocumentationSection();
              BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
              // Build tagged-tuple variant list.
              // Each member becomes: {tag, member_type()}
              // Final variant: {unknown, binary()}
              List<String> variants =
                  shape.members().stream()
                      .map(
                          m -> {
                            Symbol memberSymbol = sp.toSymbol(m);
                            String tag =
                                memberSymbol.getProperty("unionTag", String.class).orElseThrow();
                            String type = renderErlangType(memberSymbol);
                            return "{" + tag + ", " + type + "}";
                          })
                      .collect(Collectors.toList());
              variants.add("{unknown, binary()}");

              writer.write(
                  "$L",
                  ErlangRenderer.render(
                      Header.ofEntries(
                          List.of(
                              new HeaderTypeAliasEntry(
                                  TypeAlias.union(symbol.getName(), variants))),
                          true)));
              writer.popState();
            });
  }

  /**
   * Generates the -record and -type declarations for a Smithy structure shape.
   *
   * <p>Output format: -record(basic_item, { name :: basic_string(), count :: basic_integer() |
   * undefined }). -type basic_item() :: #basic_item{}.
   */
  @Override
  public void generateStructure(GenerateStructureDirective<ErlangContext, BeamSettings> directive) {
    StructureShape shape = directive.shape();
    ErlangContext ctx = directive.context();
    Symbol symbol = ctx.symbolProvider().toSymbol(shape);
    String definitionFile = symbol.getDefinitionFile();

    ctx.writerDelegator()
        .useFileWriter(
            definitionFile,
            writer -> {
              writer.pushGeneratedDocumentationSection();
              BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
              writer.popState();

              Header header =
                  buildStructureTypeHeader(
                      directive.model(), directive.service(), shape, directive.settings());
              // Smithy's writer.write(String) runs the string through CodeFormatter, so
              // literal {, }, and $ are treated as template syntax.
              // To bypass the formatter, write the string as a literal format argument.
              writer.write("$L", ErlangRenderer.render(header));
            });
  }

  static Header buildStructureTypeHeader(
      Model model, ServiceShape service, StructureShape shape, BeamSettings settings) {
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ErlangSymbolProvider(
                settings, model, service, layout.typesHeaderFile(), BeamCodegenKind.TYPES));
    NullableIndex nullableIndex = NullableIndex.of(model);
    String recordName = sp.toSymbol(shape).getName().replace("()", "");
    return Header.ofEntries(
        List.of(
            new HeaderRecordEntry(buildStructureRecord(shape, sp, nullableIndex, recordName)),
            new HeaderTypeAliasEntry(TypeAlias.of(recordName, "#" + recordName + "{}"))),
        true);
  }

  static RecordDef buildStructureRecord(
      StructureShape shape, SymbolProvider sp, NullableIndex nullableIndex, String recordName) {
    List<MemberShape> members = StreamSupport.stream(shape.members().spliterator(), false).toList();
    if (members.isEmpty()) {
      return RecordDef.of(recordName, List.of());
    }
    List<TypedField> fields = new ArrayList<>();
    for (MemberShape member : members) {
      Symbol memberSymbol = sp.toSymbol(member);
      String fieldName = memberSymbol.getProperty("fieldName", String.class).orElseThrow();
      List<String> fieldComments = new ArrayList<>();
      BeamDocumentation.forShape(member)
          .ifPresent(
              doc -> {
                fieldComments.add("@doc " + fieldName);
                for (String line : doc.split("\n", -1)) {
                  fieldComments.add(line.isEmpty() ? "" : "  " + line);
                }
              });
      String memberType = renderErlangType(memberSymbol);
      boolean nullable = BeamMemberNullability.isMemberNullable(nullableIndex, shape, member);
      String typeSpec = nullable ? memberType + " | undefined" : memberType;
      fields.add(
          fieldComments.isEmpty()
              ? TypedField.of(fieldName, typeSpec)
              : TypedField.of(fieldName, typeSpec, null, fieldComments));
    }
    return RecordDef.of(recordName, fields);
  }

  static RecordDef buildErrorRecord(
      StructureShape shape,
      SymbolProvider sp,
      NullableIndex ni,
      ErrorTrait errorTrait,
      boolean isRetryable,
      boolean isThrottling) {
    String recordName = sp.toSymbol(shape).getName().replace("()", "");
    List<TypedField> fields = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      Symbol memberSym = sp.toSymbol(member);
      String fieldName =
          memberSym
              .getProperty("fieldName", String.class)
              .orElse(BeamNameUtils.toSnakeCase(member.getMemberName()));
      String typeStr = memberSym.getName();
      if (ni.isMemberNullable(member, NullableIndex.CheckMode.CLIENT)) {
        typeStr = typeStr + " | undefined";
      }
      fields.add(TypedField.of(fieldName, typeStr));
    }
    List<String> meta =
        List.of(
            "fault: "
                + errorTrait.getValue()
                + " | retryable: "
                + isRetryable
                + " | throttling: "
                + isThrottling);
    String kind = errorTrait.getValue().equals("client") ? "client" : "server";
    fields.add(TypedField.of("'__beam_error_kind'", "client | server", kind, meta));
    return RecordDef.of(recordName, fields);
  }

  /**
   * Emits {@code -record} and {@code -type} for {@code @error} structures, including fault kind and
   * retryable metadata on the record.
   */
  @Override
  public void generateError(GenerateErrorDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    StructureShape shape = directive.shape();
    String recordName = ctx.symbolProvider().toSymbol(shape).getName().replace("()", "");
    ErrorTrait errorTrait = shape.expectTrait(ErrorTrait.class);
    BeamRetryIndex.RetryInfo retryInfo = BeamRetryIndex.forError(shape).orElseThrow();
    boolean isRetryable = retryInfo.retryable();
    boolean isThrottling = retryInfo.throttling();

    ctx.writerDelegator()
        .useFileWriter(
            new BeamErlangLayout(
                    ctx.settings(), ctx.service().getId().getNamespace(), ctx.service())
                .typesHeaderFile(),
            writer -> {
              List<HeaderEntry> entries = new ArrayList<>();
              for (String comment : shapeDocPreamble(shape)) {
                entries.add(new HeaderComment(comment));
              }
              entries.add(new HeaderBlankLine());
              entries.add(
                  new HeaderComment(
                      "Error shape: " + shape.getId() + " (" + errorTrait.getValue() + ")"));
              RecordDef record =
                  buildErrorRecord(
                      shape,
                      ctx.symbolProvider(),
                      NullableIndex.of(ctx.model()),
                      errorTrait,
                      isRetryable,
                      isThrottling);
              entries.add(new HeaderRecordEntry(record));
              entries.add(
                  new HeaderTypeAliasEntry(TypeAlias.of(recordName, "#" + recordName + "{}")));
              writer.write("$L", ErlangRenderer.render(Header.ofEntries(entries, true)));
            });
  }
}
