package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamProtocolSupport;
import io.smithy.beam.core.BeamAwsJson10ProtocolCodegen;
import io.smithy.beam.core.BeamAwsJson11ProtocolCodegen;
import io.smithy.beam.core.BeamAwsQueryProtocolCodegen;
import io.smithy.beam.core.BeamEc2QueryProtocolCodegen;
import io.smithy.beam.core.BeamRestJson1ProtocolCodegen;
import io.smithy.beam.core.BeamRestXmlProtocolCodegen;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.core.BeamSigV4Index;
import io.smithy.beam.core.BeamSigV4Metadata;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        String serviceName = directive.service().getId().getName();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout = new BeamElixirLayout(settings, ns, serviceName);
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
        String serviceName = service.getId().getName();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout = new BeamElixirLayout(settings, ns, serviceName);
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
                resolvedProtocolTraitId,
                clientModuleName,
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
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), ns, service.getId().getName());
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        String runtimeTypesModule =
                ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());

        ctx.writerDelegator().useFileWriter(
                layout.runtimeTypesModuleFile(),
                w -> {
                    Optional<String> ruleSet =
                            BeamEndpointRuleSetEmitter.serializeRuleSetJson(
                                    directive.model(), service);
                    ElixirRuntimeTypesEmitter.writeBody(w, runtimeTypesModule, ruleSet);
                });

        ctx.writerDelegator().useFileWriter(layout.clientModuleFile(), writer -> {
            writer.pushModuleHeaderSection();
            writer.write("defmodule $L do", ctx.moduleName());
            writer.popState();

            writer.indent();

            writer.pushGeneratedDocumentationSection();
            writer.openBlock("@moduledoc \"\"\"");
            ElixirFormat.writeHeredocBody(
                    writer,
                    List.of(
                            "Generated Elixir client for " + service.getId() + ".",
                            "",
                            "Operation stubs accept config and input. Transport and protocol are not generated here."));
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
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirAwsJson10Emitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirAwsJson11Emitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamAwsQueryProtocolCodegen.AWS_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirAwsQueryEmitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamEc2QueryProtocolCodegen.EC2_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirEc2QueryEmitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamRestXmlProtocolCodegen.REST_XML.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ElixirRestXmlEmitter.emitCodecModule(ctx, directive.shape());
        }

        ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        ElixirS3EndpointEmitter.emit(ctx, service);
        ElixirAwsEndpointRulesEmitter.emitIfNeeded(ctx, service);
        ElixirEndpointRulesEmitter.emit(ctx, service);
        ElixirHttpDispatchEmitter.emit(ctx, service);
        ElixirSigV4Emitter.emit(ctx, service);
        ElixirPresignerEmitter.emit(ctx, service);
        ElixirCredentialProviderEmitter.emit(ctx, service);
        ElixirPaginatorEmitter.emit(ctx, service);
        ElixirRetryEmitter.emit(ctx, service);
        ElixirWaiterEmitter.emit(ctx, service);
        ElixirComplianceTestEmitter.emit(ctx, service);
        ElixirEventStreamEmitter.emit(ctx, service);
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
            BeamAwsServiceMetadata.from(service).ifPresent(meta -> {
                writer.write("# AWS service metadata from model:");
                writer.write("#   sdkId: $L", meta.sdkId());
                writer.write("#   endpointPrefix: $L", meta.endpointPrefix());
                BeamSigV4Index sigv4Index = BeamSigV4Index.of(ctx.model(), service);
                List<OperationShape> unsignedOps =
                        sigv4Index.operationsWithUnsignedPayload(ctx.model(), service);
                writer.write("def default_config do");
                writer.write("  %{");
                writer.write("    region: \"us-east-1\",");
                writer.write("    endpoint_prefix: \"$L\",", meta.endpointPrefix());
                if (unsignedOps.isEmpty()) {
                    writer.write("    signing_name: \"$L\"", meta.signingName());
                } else {
                    writer.write("    signing_name: \"$L\",", meta.signingName());
                    for (int i = 0; i < unsignedOps.size(); i++) {
                        Symbol opSym = directive.symbolProvider().toSymbol(unsignedOps.get(i));
                        if (i == unsignedOps.size() - 1) {
                            writer.write("    {:unsigned_payload, :$L} => true", opSym.getName());
                        } else {
                            writer.write("    {:unsigned_payload, :$L} => true,", opSym.getName());
                        }
                    }
                }
                writer.write("  }");
                writer.write("end");
                writer.write("");
            });
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
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), ns, ctx.service().getId().getName());
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);
        String inType = ElixirTopDown.structureSpecType(typesModuleName, inSym);
        String outType = ElixirTopDown.structureSpecType(typesModuleName, outSym);

        boolean hasProtocol = BeamProtocolSupport.hasWireCodegen(
                ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations());
        boolean sigv4 = BeamSigV4Metadata.from(ctx.service()).isPresent();
        String sigv4Module = ElixirSymbolProvider.toModuleName(layout.sigv4ModuleName());

        BeamDocumentation.forShape(op).ifPresent(doc -> {
            ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
                writer.pushOperationBodySection();
                ElixirFormat.writeDocAttribute(writer, "@doc", doc);
                writer.popState();
            });
        });

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    opSym.getName(),
                    "client_config(), " + inType,
                    "{:ok, " + outType + "} | {:error, term()}");
            if (hasProtocol) {
                String codecMod = ElixirSymbolProvider.toModuleName(
                        layout.clientCodecModuleName(
                                ctx.resolvedProtocolTraitId(), ctx.integrations()));
                String httpMod = ElixirSymbolProvider.toModuleName(
                        layout.runtimeHttpModuleName());
                writer.write("def $L(config, input) do", opSym.getName());
                writer.indent();
                if (ElixirRestJson1Emitter.serviceHasHostLabelOperations(ctx.model(), ctx.service())
                        || ElixirRestXmlEmitter.serviceEncodesWithConfig(ctx.model(), ctx.service())) {
                    writer.write("req = $L.encode_$L_request(config, input)", codecMod, opSym.getName());
                } else {
                    writer.write("req = $L.encode_$L_request(input)", codecMod, opSym.getName());
                }
                if (sigv4) {
                    writer.write("signed_req =");
                    writer.indent();
                    writer.write("case Map.get(config, :credentials) do");
                    writer.indent();
                    writer.write("nil -> req");
                    writer.write("_ -> $L.sign(config, :$L, req)", sigv4Module, opSym.getName());
                    writer.dedent();
                    writer.write("end");
                    writer.dedent();
                    writer.write("");
                    writer.write("case $L.dispatch(config, signed_req) do", httpMod);
                } else {
                    writer.write("");
                    writer.write("case $L.dispatch(config, req) do", httpMod);
                }
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
