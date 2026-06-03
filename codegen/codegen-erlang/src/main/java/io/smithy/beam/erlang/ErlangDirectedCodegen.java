package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamMemberNullability;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamDocumentation.DocTarget;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.*;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.*;

import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.traits.EnumValueTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * DirectedCodegen implementation for the Erlang types generator.
 *
 * <p>Constraint traits do not narrow Dialyzer types; see {@link io.smithy.beam.core.BeamConstraintPolicy}.
 *
 * CodegenDirector calls methods in this order:
 *   1. customizeBeforeShapeGeneration  -- file header + scalar/list/map type aliases
 *   2. generate* methods in topological order (enums, unions, structures)
 *   3. customizeBeforeIntegrations
 *   4. integration.customize() calls
 *   5. customizeAfterIntegrations
 *   6. flushWriters
 *
 * generateService is a stub reserved for client/server generation.
 * Resource helpers are emitted by client/server DirectedCodegen classes.
 */
final class ErlangDirectedCodegen
        implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

    // ── Factory methods ──────────────────────────────────────────────────────

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns);
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
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns);
        String definitionFile = layout.typesHeaderFile();
        String moduleName = layout.modulePrefix();
        return new ErlangContext(
                directive.model(),
                directive.settings(),
                directive.symbolProvider(),
                directive.fileManifest(),
                new WriterDelegator<>(
                        directive.fileManifest(),
                        directive.symbolProvider(),
                        ErlangWriter.factory()),
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
     * Runs before any generate* method.
     * Writes the file header comment and scalar/list/map type aliases
     * by iterating named shapes in the service closure.
     */
    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        Model model = directive.model();
        Set<Shape> closure = new Walker(model).walkShapes(directive.service());
        Set<ShapeId> preambleAliasesEmitted = new LinkedHashSet<>();

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.forShape(directive.service()).ifPresentOrElse(
                    doc -> BeamDocumentation.writeErlangDoc(writer, doc),
                    () -> {
                        writer.write("%% Record and type definitions for the $L model.", ctx.moduleName());
                        writer.write("%% ");
                    });
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

            assertPreambleAliasCoverage(closure, preambleAliasesEmitted);
        });
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

    /**
     * Shape ids that must receive exactly one preamble {@code -type} alias for the given closure.
     */
    static Set<ShapeId> expectedPreambleAliasShapeIds(Set<Shape> closure) {
        return closure.stream()
                .filter(ErlangDirectedCodegen::receivesPreambleTypeAlias)
                .map(Shape::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void recordPreambleAlias(Shape shape, Set<ShapeId> emitted) {
        if (!emitted.add(shape.getId())) {
            assert false : "duplicate preamble alias for " + shape.getId();
        }
    }

    private static void assertPreambleAliasCoverage(Set<Shape> closure, Set<ShapeId> emitted) {
        Set<ShapeId> expected = expectedPreambleAliasShapeIds(closure);
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
                .filter(ErlangDirectedCodegen::receivesPreambleTypeAlias)
                .sorted(java.util.Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    recordPreambleAlias(s, preambleAliasesEmitted);
                    Symbol sym = symbolProvider.toSymbol(s);
                    String baseType = sym.getProperty("baseType", String.class).orElse("term()");
                    writer.pushGeneratedDocumentationSection();
                    BeamDocumentation.writeShapeDocIfPresent(writer, s, DocTarget.ERLANG);
                    if (s instanceof BigDecimalShape) {
                        writer.write("-type $L :: $L.       %% decimal:decimal()", sym.getName(), baseType);
                    } else if (s instanceof BlobShape
                            && sym.getProperty("streamingBlob", Boolean.class).orElse(false)) {
                        writer.write(
                                "-type $L :: $L.       %% streaming payload; framing deferred to protocol layer",
                                sym.getName(),
                                baseType);
                    } else {
                        writer.write("-type $L :: $L.", sym.getName(), baseType);
                    }
                    writer.popState();
                });
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
                .forEach(s -> {
                    recordPreambleAlias(s, preambleAliasesEmitted);
                    Symbol sym = symbolProvider.toSymbol(s);
                    Symbol memberSym = symbolProvider.toSymbol(s.getMember());
                    String elementType = renderErlangType(memberSym);
                    if (s.hasTrait(SparseTrait.ID)) {
                        elementType = elementType + " | undefined";
                    }
                    writer.pushGeneratedDocumentationSection();
                    BeamDocumentation.writeShapeDocIfPresent(writer, s, DocTarget.ERLANG);
                    writer.write("-type $L :: [$L].", sym.getName(), elementType);
                    writer.popState();
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
                .forEach(s -> {
                    recordPreambleAlias(s, preambleAliasesEmitted);
                    Symbol sym = symbolProvider.toSymbol(s);
                    Symbol keySym = symbolProvider.toSymbol(s.getKey());
                    Symbol valueSym = symbolProvider.toSymbol(s.getValue());
                    String valueType = renderErlangType(valueSym);
                    if (s.hasTrait(SparseTrait.ID)) {
                        valueType = valueType + " | undefined";
                    }
                    writer.pushGeneratedDocumentationSection();
                    BeamDocumentation.writeShapeDocIfPresent(writer, s, DocTarget.ERLANG);
                    writer.write("-type $L :: #{$L => $L}.",
                            sym.getName(), renderErlangType(keySym), valueType);
                    writer.popState();
                });
    }

    /**
     * Resolves a symbol to an Erlang type reference inside generated {@code -type} bodies.
     * Built-in shapes use the symbol name. Other shapes honor {@code typeKind} on the symbol
     * ({@code alias} for lists, maps, unions, and named scalars; {@code module} for structures,
     * enums, and int enums). All named service types share one header file, so both kinds
     * currently render as the type alias name from {@link Symbol#getName()}.
     */
    private String renderErlangType(Symbol symbol) {
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
        // No action required for the types-only baseline.
    }

    // ── Service / Resource / Operation stubs ─────────────────────────────────
    // Reserved for future client/server generation. The types-only plugin does
    // not generate service, resource, or operation code.

    /**
     * Types pass: service clients and servers are emitted by
     * {@link ErlangClientDirectedCodegen} and {@link ErlangServerDirectedCodegen}.
     */
    @Override
    public void generateService(
            GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
        // Client/server passes own service emission.
    }

    /**
     * Types pass: resource helpers are emitted by client/server DirectedCodegen classes.
     */
    @Override
    public void generateResource(
            GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
        // Client/server passes own resource emission.
    }

    // ── Type generation ──────────────────────────────────────────────────────

    /**
     * Generates the -type declaration for a Smithy enum shape.
     *
     * Output format:
     *   -type basic_status() :: active | inactive | pending | {unknown, binary()}.
     */
    @Override
    public void generateEnumShape(
            GenerateEnumDirective<ErlangContext, BeamSettings> directive) {
        EnumShape shape = directive.expectEnumShape();
        ErlangContext ctx = directive.context();
        SymbolProvider sp = directive.symbolProvider();
        Symbol symbol = sp.toSymbol(shape);
        String definitionFile = symbol.getDefinitionFile();

        List<String> atoms = symbol.getProperty("enumAtoms", List.class).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, String> atomByMember = symbol.getProperty("enumAtomByMember", Map.class).orElseThrow();

        ctx.writerDelegator().useFileWriter(definitionFile, writer -> {
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
            // Build "active | inactive | pending | {unknown, binary()}"
            String variants = String.join(" | ", atoms) + " | {unknown, binary()}";
            writer.write("-type $L :: $L.", symbol.getName(), variants);
            writer.write("%% Wire values for $L:", shape.getId());
            for (MemberShape m : shape.members()) {
                String wireValue = m.getTrait(EnumValueTrait.class)
                        .flatMap(EnumValueTrait::getStringValue)
                        .orElse(m.getMemberName());
                String atom = atomByMember.get(m.getMemberName());
                writer.write("%%   $L -> <<\"$L\">>", atom, wireValue);
            }
            writer.popState();
        });
    }

    /**
     * Generates the -type declaration for a Smithy intEnum shape.
     *
     * Output format:
     *   -type basic_priority() :: low | medium | high | {unknown, integer()}.
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

        ctx.writerDelegator().useFileWriter(definitionFile, writer -> {
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
            String variants = String.join(" | ", atoms) + " | {unknown, integer()}";
            writer.write("-type $L :: $L.", symbol.getName(), variants);
            writer.popState();
        });
    }

    /**
     * Generates the -type declaration for a Smithy union shape.
     *
     * Output format:
     *   -type basic_union() ::
     *       {text, basic_string()} |
     *       {number, basic_integer()} |
     *       {unknown, binary()}.
     */
    @Override
    public void generateUnion(
            GenerateUnionDirective<ErlangContext, BeamSettings> directive) {
        UnionShape shape = directive.shape();
        ErlangContext ctx = directive.context();
        SymbolProvider sp = directive.symbolProvider();
        Symbol symbol = sp.toSymbol(shape);
        String definitionFile = symbol.getDefinitionFile();

        ctx.writerDelegator().useFileWriter(definitionFile, writer -> {
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
            // Build tagged-tuple variant list.
            // Each member becomes: {tag, member_type()}
            // Final variant: {unknown, binary()}
            List<String> variants = shape.members().stream()
                    .map(m -> {
                        Symbol memberSymbol = sp.toSymbol(m);
                        String tag = memberSymbol.getProperty("unionTag", String.class).orElseThrow();
                        String type = renderErlangType(memberSymbol);
                        return "{" + tag + ", " + type + "}";
                    })
                    .collect(Collectors.toList());
            variants.add("{unknown, binary()}");

            if (variants.size() <= 2) {
                // Single-line form
                writer.write("-type $L :: $L.", symbol.getName(), String.join(" | ", variants));
            } else {
                // Multi-line form matching Erlang convention
                writer.write("-type $L ::", symbol.getName());
                for (int i = 0; i < variants.size(); i++) {
                    String sep = (i < variants.size() - 1) ? "  |" : ".";
                    writer.write("    $L$L", variants.get(i), sep);
                }
            }
            writer.popState();
        });
    }

    /**
     * Generates the -record and -type declarations for a Smithy structure shape.
     *
     * Output format:
     *   -record(basic_item, {
     *       name  :: basic_string(),
     *       count :: basic_integer() | undefined
     *   }).
     *   -type basic_item() :: #basic_item{}.
     */
    @Override
    public void generateStructure(
            GenerateStructureDirective<ErlangContext, BeamSettings> directive) {
        StructureShape shape = directive.shape();
        ErlangContext ctx = directive.context();
        SymbolProvider sp = directive.symbolProvider();
        NullableIndex nullableIndex = NullableIndex.of(directive.model());
        Symbol symbol = sp.toSymbol(shape);
        // Strip trailing "()" to get the record name: "basic_item" from "basic_item()"
        String recordName = symbol.getName().replace("()", "");
        String definitionFile = symbol.getDefinitionFile();

        ctx.writerDelegator().useFileWriter(definitionFile, writer -> {
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);

            List<MemberShape> members =
                    StreamSupport.stream(shape.members().spliterator(), false).toList();
            if (members.isEmpty()) {
                writer.write("-record($L, {}).", recordName);
            } else {
                writer.openBlock("-record($L, {", recordName);
                for (int i = 0; i < members.size(); i++) {
                    MemberShape member = members.get(i);
                    BeamDocumentation.forShape(member).ifPresent(doc -> {
                        String fieldName = sp.toSymbol(member)
                                .getProperty("fieldName", String.class).orElseThrow();
                        writer.write("%% @doc $L", fieldName);
                        for (String line : doc.split("\n", -1)) {
                            if (line.isEmpty()) {
                                writer.write("%%");
                            } else {
                                writer.write("%%   $L", line);
                            }
                        }
                    });
                    Symbol memberSymbol = sp.toSymbol(member);
                    String fieldName = memberSymbol.getProperty("fieldName", String.class).orElseThrow();
                    String memberType = renderErlangType(memberSymbol);
                    boolean nullable = BeamMemberNullability.isMemberNullable(nullableIndex, shape, member);
                    String typeSpec = nullable ? memberType + " | undefined" : memberType;
                    String comma = (i < members.size() - 1) ? "," : "";
                    writer.write("$L :: $L$L", fieldName, typeSpec, comma);
                }
                writer.closeBlock("}).");
            }
            writer.write("-type $L :: #$L{}.", symbol.getName(), recordName);
            writer.popState();
        });
    }

    /**
     * Emits {@code -record} and {@code -type} for {@code @error} structures, including
     * fault kind and retryable metadata on the record.
     */
    @Override
    public void generateError(
            GenerateErrorDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        StructureShape shape = directive.shape();
        String recordName = ctx.symbolProvider().toSymbol(shape).getName().replace("()", "");
        ErrorTrait errorTrait = shape.expectTrait(ErrorTrait.class);
        BeamRetryIndex.RetryInfo retryInfo = BeamRetryIndex.forError(shape).orElseThrow();
        boolean isRetryable = retryInfo.retryable();
        boolean isThrottling = retryInfo.throttling();

        ctx.writerDelegator().useFileWriter(
                new BeamErlangLayout(ctx.settings(), ctx.service().getId().getNamespace())
                        .typesHeaderFile(),
                writer -> {
                    writer.pushGeneratedDocumentationSection();
                    BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ERLANG);
                    writer.popState();
                    writer.write("");
                    writer.write("%% Error shape: $L ($L)", shape.getId(), errorTrait.getValue());
                    writer.write("-record($L, {", recordName);
                    NullableIndex ni = NullableIndex.of(ctx.model());
                    for (MemberShape member : shape.members()) {
                        Symbol memberSym = ctx.symbolProvider().toSymbol(member);
                        String typeStr = memberSym.getName();
                        if (ni.isMemberNullable(member, NullableIndex.CheckMode.CLIENT)) {
                            writer.write("    $L :: $L | undefined,", member.getMemberName(), typeStr);
                        } else {
                            writer.write("    $L :: $L,", member.getMemberName(), typeStr);
                        }
                    }
                    writer.write("    %% fault: $L | retryable: $L | throttling: $L",
                            errorTrait.getValue(),
                            isRetryable,
                            isThrottling);
                    writer.write("    '__beam_error_kind' = $L :: $L",
                            errorTrait.getValue().equals("client") ? "client" : "server",
                            "client | server");
                    writer.write("}).");
                    writer.write("-type $L() :: #$L{}.", recordName, recordName);
                });
    }
}
