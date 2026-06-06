package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamAwsJson10ProtocolCodegen;
import io.smithy.beam.core.BeamAwsJson11ProtocolCodegen;
import io.smithy.beam.core.BeamAwsQueryProtocolCodegen;
import io.smithy.beam.core.BeamEc2QueryProtocolCodegen;
import io.smithy.beam.core.BeamRestJson1ProtocolCodegen;
import io.smithy.beam.core.BeamRestXmlProtocolCodegen;
import io.smithy.beam.core.BeamResourceIndex;
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
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.Optional;

/**
 * Server-specific DirectedCodegen pass. Types are emitted by {@link ElixirTypeGeneration}
 * before this runs; this class must not write type files again.
 */
final class ElixirServerDirectedCodegen
        implements DirectedCodegen<ElixirContext, BeamSettings, ElixirIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout =
                new BeamElixirLayout(settings, ns, directive.service());
        String definitionFile = layout.serverModuleFile();
        String serverModuleName = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
        return SymbolProvider.cache(
                new ElixirSymbolProvider(
                        settings,
                        directive.model(),
                        directive.service(),
                        definitionFile,
                        serverModuleName,
                        BeamCodegenKind.SERVER));
    }

    @Override
    public ElixirContext createContext(
            CreateContextDirective<BeamSettings, ElixirIntegration> directive) {
        ServiceShape service = directive.service();
        BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
        Optional<ShapeId> resolved =
                BeamProtocolResolver.resolve(directive.model(), service, directive.settings());
        ShapeId resolvedProtocolTraitId = resolved.orElse(null);
        BeamProtocolCodegen protocolCodegen = null;
        if (resolved.isPresent()) {
            protocolCodegen =
                    BeamProtocolCodegenFactory.create(
                            directive.model(), resolved.get(), directive.integrations());
        }
        String ns = service.getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout =
                new BeamElixirLayout(settings, ns, directive.service());
        String definitionFile = layout.serverModuleFile();
        String serverModuleName = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
        return new ElixirContext(
                directive.model(),
                directive.settings(),
                directive.symbolProvider(),
                directive.fileManifest(),
                new WriterDelegator<>(
                        directive.fileManifest(),
                        directive.symbolProvider(),
                        ElixirWriter.factory(serverModuleName)),
                directive.integrations(),
                service,
                httpBindings,
                protocolCodegen,
                resolvedProtocolTraitId,
                serverModuleName,
                definitionFile);
    }

    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        ServiceShape service = ctx.service();
        BeamEdition edition = BeamEdition.fromSettings(directive.settings());
        BeamProtocolResolver.resolve(directive.model(), service, directive.settings())
                .ifPresent(
                        protocol ->
                                BeamProtocolResolver.assertClosureSupported(
                                        directive.model(), service, protocol, edition));

        String ns = service.getId().getNamespace();
        BeamElixirLayout layout =
                new BeamElixirLayout(ctx.settings(), ns, service);
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        String runtimeTypesModule =
                ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());

        ctx.writerDelegator().useFileWriter(
                layout.runtimeTypesModuleFile(),
                w -> ElixirRuntimeTypesEmitter.writeBody(w, runtimeTypesModule, Optional.empty()));

        ctx.writerDelegator().useFileWriter(layout.serverModuleFile(), writer -> {
            writer.pushModuleHeaderSection();
            writer.write("defmodule $L do", ctx.moduleName());
            writer.popState();

            writer.indent();

            writer.pushGeneratedDocumentationSection();
            writer.openBlock("@moduledoc \"\"\"");
            writer.write("Generated Elixir server stub for $L.", service.getId());
            writer.write("");
            writer.write("Handlers are model-agnostic at runtime; names follow Smithy operations.");
            writer.closeBlock("\"\"\"");
            writer.popState();

            writer.pushDependenciesSection();
            writer.write("alias $L", typesModuleName);
            writer.popState();
        });
    }

    @Override
    public void customizeBeforeIntegrations(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        // Reserved for server integrations.
    }

    @Override
    public void customizeAfterIntegrations(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.dedent();
            writer.pushModuleHeaderSection();
            writer.write("end");
            writer.popState();
        });
    }

    @Override
    public void generateService(
            GenerateServiceDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        ServiceShape service = directive.shape();

        if (ctx.protocolCodegen() != null
                && BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirRestJson1Emitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamRestXmlProtocolCodegen.REST_XML.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirRestXmlEmitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirAwsJson10Emitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirAwsJson11Emitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamAwsQueryProtocolCodegen.AWS_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirAwsQueryEmitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamEc2QueryProtocolCodegen.EC2_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirEc2QueryEmitter.emitServerCodecModule(ctx, service);
        }

        ElixirRouterEmitter.emit(ctx, service);
        ElixirComplianceTestEmitter.emit(ctx, service);
        BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
        for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
            ElixirResourceEmitter.emitServer(ctx, resource);
        }

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write(
                    "# Dispatch and routing modules should map wire metadata to $L below.",
                    ctx.moduleName());
            writer.write("# No Smithy shapes are referenced at runtime in this baseline.");
            writer.write("");
            writer.popState();
        });
    }

    @Override
    public void generateOperation(
            GenerateOperationDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        OperationShape op = directive.shape();
        SymbolProvider sp = directive.symbolProvider();
        Symbol opSym = sp.toSymbol(op);
        String handler = "handle_" + opSym.getName();

        String ns = ctx.service().getId().getNamespace();
        BeamElixirLayout layout =
                new BeamElixirLayout(ctx.settings(), ns, ctx.service());
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);
        String inType = ElixirTopDown.structureSpecType(typesModuleName, inSym);
        String outType = ElixirTopDown.structureSpecType(typesModuleName, outSym);

        BeamDocumentation.forShape(op).ifPresent(doc -> {
            ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
                writer.pushOperationBodySection();
                BeamDocumentation.writeElixirDoc(writer, doc);
                writer.popState();
            });
        });

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write(
                    "@spec $L(term(), $L, term()) :: {:ok, $L} | {:error, term()}",
                    handler,
                    inType,
                    outType);
            writer.write(
                    "def $L(_ctx, _input, _meta), do: {:error, :not_implemented}", handler);
            writer.write("");
            writer.popState();
        });
    }

    @Override
    public void generateResource(
            GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
        // Emitted from generateService for all contained resources.
    }

    @Override
    public void generateEnumShape(
            GenerateEnumDirective<ElixirContext, BeamSettings> directive) {
        // Types-only: handled by ElixirTypeGeneration.
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ElixirContext, BeamSettings> directive) {
        // Types-only: handled by ElixirTypeGeneration.
    }

    @Override
    public void generateUnion(
            GenerateUnionDirective<ElixirContext, BeamSettings> directive) {
        // Types-only: handled by ElixirTypeGeneration.
    }

    @Override
    public void generateStructure(
            GenerateStructureDirective<ElixirContext, BeamSettings> directive) {
        // Types-only: handled by ElixirTypeGeneration.
    }

    @Override
    public void generateError(
            GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
        // Types-only: handled by ElixirTypeGeneration.
    }
}
