package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamServiceIndex;
import io.smithy.beam.core.BeamSettings;
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
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Client-specific DirectedCodegen. Types are emitted by {@link ErlangTypeGeneration}
 * before this runs; this class must not write type files again.
 */
final class ErlangClientDirectedCodegen
        implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns);
        String definitionFile = layout.clientModuleFile();
        return SymbolProvider.cache(
                new ErlangSymbolProvider(
                        settings,
                        directive.model(),
                        directive.service(),
                        definitionFile,
                        BeamCodegenKind.CLIENT));
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

    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        // Intentionally empty: ErlangTypeGeneration already wrote shared headers and aliases.
    }

    @Override
    public void customizeBeforeIntegrations(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        // Reserved for client integrations.
    }

    @Override
    public void customizeAfterIntegrations(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        // Reserved for client integrations.
    }

    @Override
    public void generateService(
            GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        ServiceShape service = directive.shape();
        String ns = service.getId().getNamespace();
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), ns);
        String clientFile = layout.clientModuleFile();

        ctx.writerDelegator().useFileWriter(clientFile, writer -> {
            writer.write("%% Generated Erlang client stub for $L.", service.getId());
            writer.write(
                    "%% TopDown operation count: $L",
                    BeamServiceIndex.of(ctx.model()).containedOperations(service).size());
            writer.write("%% TODO: operation functions, encoding, and configuration.");
            writer.write("-module($L).", layout.clientModuleName());
            writer.write("-export([]).");
        });
    }

    @Override
    public void generateResource(
            GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
        // Reserved for resource helpers.
    }

    @Override
    public void generateEnumShape(
            GenerateEnumDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateUnion(
            GenerateUnionDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateStructure(
            GenerateStructureDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateError(
            GenerateErrorDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }
}
