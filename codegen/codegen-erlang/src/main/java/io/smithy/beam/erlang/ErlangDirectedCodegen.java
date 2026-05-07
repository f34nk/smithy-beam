package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.*;

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
        // TODO: implement in a later commit.
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
