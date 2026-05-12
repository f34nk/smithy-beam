package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
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
 * Client-specific DirectedCodegen. Types are emitted by {@link ElixirTypeGeneration}
 * before this runs; this class must not write type files again.
 */
final class ElixirClientDirectedCodegen
        implements DirectedCodegen<ElixirContext, BeamSettings, ElixirIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout = new BeamElixirLayout(settings, ns);
        String definitionFile = layout.clientModuleFile();
        String clientModuleName = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_client");
        return SymbolProvider.cache(
                new ElixirSymbolProvider(
                        settings,
                        directive.model(),
                        directive.service(),
                        definitionFile,
                        clientModuleName,
                        BeamCodegenKind.CLIENT));
    }

    @Override
    public ElixirContext createContext(
            CreateContextDirective<BeamSettings, ElixirIntegration> directive) {
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout = new BeamElixirLayout(settings, ns);
        String definitionFile = layout.clientModuleFile();
        String clientModuleName = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_client");
        return new ElixirContext(
                directive.model(),
                directive.settings(),
                directive.symbolProvider(),
                directive.fileManifest(),
                new WriterDelegator<>(
                        directive.fileManifest(),
                        directive.symbolProvider(),
                        ElixirWriter.factory(clientModuleName)),
                directive.integrations(),
                directive.service(),
                clientModuleName,
                definitionFile);
    }

    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        // Intentionally empty: ElixirTypeGeneration already wrote the types module.
    }

    @Override
    public void customizeBeforeIntegrations(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        // Reserved for client integrations.
    }

    @Override
    public void customizeAfterIntegrations(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        // Reserved for client integrations.
    }

    @Override
    public void generateService(
            GenerateServiceDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        ServiceShape service = directive.shape();
        String ns = service.getId().getNamespace();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns);
        String clientFile = layout.clientModuleFile();

        ctx.writerDelegator().useFileWriter(clientFile, writer -> {
            writer.write("# Generated Elixir client stub for $L.", service.getId());
            writer.write(
                    "# TopDown operation count: $L",
                    BeamServiceIndex.of(ctx.model()).containedOperations(service).size());
            writer.write("# TODO: operations, encoding, and configuration.");
            writer.openBlock("defmodule $L do", ctx.moduleName());
            writer.write("@moduledoc false");
            writer.closeBlock("end");
        });
    }

    @Override
    public void generateResource(
            GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
        // Reserved for resource helpers.
    }

    @Override
    public void generateEnumShape(
            GenerateEnumDirective<ElixirContext, BeamSettings> directive) {
        // Handled by ElixirTypeGeneration.
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ElixirContext, BeamSettings> directive) {
        // Handled by ElixirTypeGeneration.
    }

    @Override
    public void generateUnion(
            GenerateUnionDirective<ElixirContext, BeamSettings> directive) {
        // Handled by ElixirTypeGeneration.
    }

    @Override
    public void generateStructure(
            GenerateStructureDirective<ElixirContext, BeamSettings> directive) {
        // Handled by ElixirTypeGeneration.
    }

    @Override
    public void generateError(
            GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
        // Handled by ElixirTypeGeneration.
    }
}
