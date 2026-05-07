package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.*;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.*;
import java.util.Set;
/**
 * DirectedCodegen implementation for the Erlang types generator.
 *
 * CodegenDirector calls methods in this order:
 *   1. customizeBeforeShapeGeneration  -- file header + scalar/list/map type aliases
 *   2. generate* methods in topological order (enums, unions, structures)
 *   3. customizeBeforeIntegrations
 *   4. integration.customize() calls
 *   5. customizeAfterIntegrations
 *   6. flushWriters
 *
 * generateService and generateResource are stubs reserved for client/server
 * generation in a future iteration. generateError fails fast because error
 * structures require error/exception semantics that are outside the initial
 * type-only scope.
 */
final class ErlangDirectedCodegen
        implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

    // ── Factory methods ──────────────────────────────────────────────────────

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        String module = directive.settings().resolveModule(ns);
        // TODO: make this configurable (outputDir relative to project root)
        String definitionFile = module + "_types.hrl";
        return SymbolProvider.cache(
                new ErlangSymbolProvider(directive.model(), directive.service(), definitionFile));
    }

    @Override
    public ErlangContext createContext(
            CreateContextDirective<BeamSettings, ErlangIntegration> directive) {
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
                directive.service());
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
        String ns = directive.service().getId().getNamespace();
        String module = ctx.settings().resolveModule(ns);
        // TODO: make this configurable (outputDir relative to project root)
        String definitionFile = module + "_types.hrl";
        Set<Shape> closure = new Walker(model).walkShapes(directive.service());

        ctx.writerDelegator().useFileWriter(definitionFile, writer -> {
            writer.write("%% Record and type definitions for the $L model.", module);
            writer.write("%% ");

            // Write named scalar type aliases in declaration order:
            // blob, boolean, string, byte, short, integer, long, float, double,
            // bigInteger, bigDecimal, timestamp, document.
            writeScalarAliases(writer, model, closure, directive.symbolProvider());

            // DirectedCodegen has no generateList or generateMap callback, so the
            // BEAM type-file aliases for list and map shapes must be written here.
            writeListAliases(writer, model, closure, directive.symbolProvider());
            writeMapAliases(writer, model, closure, directive.symbolProvider());
        });
    }

    private void writeScalarAliases(
            ErlangWriter writer,
            Model model,
            Set<Shape> closure,
            SymbolProvider symbolProvider) {

        // Iterate shape types in a defined order matching the baseline output.
        writeShapeTypeAliases(writer, model.getBlobShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getBooleanShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getStringShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getByteShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getShortShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getIntegerShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getLongShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getFloatShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getDoubleShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getBigIntegerShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getBigDecimalShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getTimestampShapes(), closure, symbolProvider);
        writeShapeTypeAliases(writer, model.getDocumentShapes(), closure, symbolProvider);
    }

    private <S extends Shape> void writeShapeTypeAliases(
            ErlangWriter writer,
            java.util.Set<S> shapes,
            Set<Shape> closure,
            SymbolProvider symbolProvider) {
        shapes.stream()
                .filter(closure::contains)
                .sorted(java.util.Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    Symbol sym = symbolProvider.toSymbol(s);
                    String baseType = sym.getProperty("baseType", String.class).orElse("term()");
                    // bigDecimal gets an explanatory comment
                    if (s instanceof BigDecimalShape) {
                        writer.write("-type $L :: $L.       %% decimal:decimal()", sym.getName(), baseType);
                    } else {
                        writer.write("-type $L :: $L.", sym.getName(), baseType);
                    }
                });
    }

    private void writeListAliases(
            ErlangWriter writer,
            Model model,
            Set<Shape> closure,
            SymbolProvider symbolProvider) {
        model.getListShapes().stream()
                .filter(closure::contains)
                .sorted(java.util.Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    Symbol sym = symbolProvider.toSymbol(s);
                    Symbol memberSym = symbolProvider.toSymbol(s.getMember());
                    writer.write("-type $L :: [$L].", sym.getName(), renderErlangType(memberSym));
                });
    }

    private void writeMapAliases(
            ErlangWriter writer,
            Model model,
            Set<Shape> closure,
            SymbolProvider symbolProvider) {
        model.getMapShapes().stream()
                .filter(closure::contains)
                .sorted(java.util.Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    Symbol sym = symbolProvider.toSymbol(s);
                    Symbol keySym = symbolProvider.toSymbol(s.getKey());
                    Symbol valueSym = symbolProvider.toSymbol(s.getValue());
                    writer.write("-type $L :: #{$L => $L}.",
                            sym.getName(), renderErlangType(keySym), renderErlangType(valueSym));
                });
    }

    private String renderErlangType(Symbol symbol) {
        boolean builtIn = symbol.getProperty("builtIn", Boolean.class).orElse(false);
        if (builtIn) {
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
     * Stub. Future: generate the Erlang client module
     * ({service_name}_client.erl) with operation stubs and request/response types.
     */
    @Override
    public void generateService(
            GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
        // TODO: generate service client module.
    }

    /**
     * Stub. Future: generate resource-level helper modules.
     */
    @Override
    public void generateResource(
            GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
        // TODO: generate resource modules.
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
        // TODO: implement in a later commit.
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
        // TODO: implement in a later commit.
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
        // TODO: implement in a later commit.
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
        // TODO: implement in a later commit.
    }

    /**
     * Error structures require error/exception semantics and retryable or
     * throttling metadata. The initial generator interprets types only, so it
     * rejects reachable error shapes instead of silently omitting them.
     */
    @Override
    public void generateError(
            GenerateErrorDirective<ErlangContext, BeamSettings> directive) {
        throw new CodegenException("Erlang error type generation is not implemented for "
                + directive.shape().getId()
                + ". The initial smithy-beam generator only emits type definitions.");
    }
}
