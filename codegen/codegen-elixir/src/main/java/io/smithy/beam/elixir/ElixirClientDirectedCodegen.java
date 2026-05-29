package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamRestJson1ProtocolCodegen;
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
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.Map;

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
        String clientModuleName = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
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
        ServiceShape service = directive.service();
        BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
        BeamProtocolCodegen protocolCodegen = null;
        if (directive.settings().protocol() != null) {
            ShapeId protocolId =
                    BeamProtocolResolver.resolve(
                            directive.model(), service, directive.settings());
            protocolCodegen =
                    BeamProtocolCodegenFactory.create(directive.model(), protocolId);
        }
        String ns = service.getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout = new BeamElixirLayout(settings, ns);
        String definitionFile = layout.clientModuleFile();
        String clientModuleName = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
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
                service,
                httpBindings,
                protocolCodegen,
                clientModuleName,
                definitionFile);
    }

    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        ServiceShape service = ctx.service();
        String ns = service.getId().getNamespace();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns);
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        String runtimeTypesModule =
                ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());

        ctx.writerDelegator().useFileWriter(
                layout.runtimeTypesModuleFile(),
                w -> ElixirRuntimeTypesEmitter.writeBody(w, runtimeTypesModule));

        ctx.writerDelegator().useFileWriter(layout.clientModuleFile(), writer -> {
            writer.pushModuleHeaderSection();
            writer.write("defmodule $L do", ctx.moduleName());
            writer.popState();

            writer.indent();

            writer.pushGeneratedDocumentationSection();
            writer.openBlock("@moduledoc \"\"\"");
            writer.write("Generated Elixir client for $L.", service.getId());
            writer.write("");
            writer.write("Operation stubs accept config and input. Transport and protocol are not generated here.");
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
        // Reserved for client integrations.
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
            ElixirRestJson1Emitter.emitCodecModule(ctx, directive.shape());
        }

        ElixirHttpDispatchEmitter.emit(ctx, service);
        ElixirPaginatorEmitter.emit(ctx, service);
        BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
        for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
            ElixirResourceEmitter.emitClient(ctx, resource);
        }

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write("# Service closure: $L", service.getId());
            writer.write(
                    "# Client configuration is intentionally opaque at this layer; "
                            + "endpoint, transport, and protocol live in future runtime modules.");
            writer.write("@type client_config :: map()");
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

        String ns = ctx.service().getId().getNamespace();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns);
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);
        String inType = ElixirTopDown.structureSpecType(typesModuleName, inSym);
        String outType = ElixirTopDown.structureSpecType(typesModuleName, outSym);

        boolean hasProtocol = ctx.protocolCodegen() != null
                && BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(
                        ctx.protocolCodegen().protocolTraitId());

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
                    "@spec $L(client_config(), $L) :: {:ok, $L} | {:error, term()}",
                    opSym.getName(),
                    inType,
                    outType);
            if (hasProtocol) {
                String codecMod = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName());
                String httpMod = ElixirSymbolProvider.toModuleName(
                        layout.runtimeHttpModuleName());
                writer.write("def $L(config, input) do", opSym.getName());
                writer.indent();
                writer.write("req = $L.encode_$L_request(input)", codecMod, opSym.getName());
                writer.write("case $L.dispatch(config, req) do", httpMod);
                writer.indent();
                writer.write("{:ok, resp} -> $L.decode_$L_response(resp)", codecMod, opSym.getName());
                writer.write("{:error, reason} -> {:error, reason}");
                writer.dedent();
                writer.write("end");
                writer.dedent();
                writer.write("end");
            } else {
                writer.write("def $L(_config, _input), do: {:error, :not_implemented}",
                        opSym.getName());
            }
            writer.write("");
            writer.popState();
        });

        if (ctx.protocolCodegen() != null) {
            ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
                ctx.protocolCodegen().emitOperationBindings(ctx, ctx.service(), op);
                writer.pushOperationBodySection();
                writer.write("# HTTP request bindings for $L:", op.getId());
                for (Map.Entry<String, HttpBinding> entry :
                        ctx.httpBindings().requestBindings(op).entrySet()) {
                    HttpBinding binding = entry.getValue();
                    writer.write("#   $L @ $L", entry.getKey(), binding.getLocation());
                }
                writer.write("");
                writer.popState();
                for (ElixirIntegration integration : ctx.integrations()) {
                    integration.customizeProtocolSerialize(ctx, op, writer);
                }
            });
        }
    }

    @Override
    public void generateResource(
            GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
        // Emitted from generateService for all contained resources.
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
