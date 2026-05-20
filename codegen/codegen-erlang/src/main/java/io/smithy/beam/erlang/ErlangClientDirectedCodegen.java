package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSettings;
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
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.ArrayList;
import java.util.List;

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
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns);
        String definitionFile = layout.clientModuleFile();
        String moduleName = layout.clientModuleName();
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
                directive.service(),
                moduleName,
                definitionFile);
    }

    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        ServiceShape service = ctx.service();
        String ns = service.getId().getNamespace();
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), ns);

        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
        List<String> exports = new ArrayList<>();
        for (OperationShape op : operations) {
            Symbol sym = directive.symbolProvider().toSymbol(op);
            exports.add(sym.getName() + "/2");
        }
        String exportList = String.join(", ", exports);

        ctx.writerDelegator().useFileWriter(layout.clientModuleFile(), writer -> {
            writer.pushGeneratedDocumentationSection();
            writer.write("%% Generated Erlang client for $L.", service.getId());
            writer.write("%% Operation stubs use arity 2: (Config, Input).");
            writer.popState();

            writer.pushModuleHeaderSection();
            writer.write("-module($L).", layout.clientModuleName());
            writer.popState();

            writer.pushDependenciesSection();
            ((ErlangImports) writer.getImportContainer()).addIncludeRelative(layout.typesHeaderFile());
            writer.write(ErlangImports.relativeIncludeLine(layout.typesHeaderFile()));
            writer.popState();

            writer.pushModuleHeaderSection();
            if (exportList.isEmpty()) {
                writer.write("-export([]).");
            } else {
                writer.write("-export([$L]).", exportList);
            }
            writer.write("");
            writer.popState();
        });
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
        // Module header and exports are written in customizeBeforeShapeGeneration.
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
