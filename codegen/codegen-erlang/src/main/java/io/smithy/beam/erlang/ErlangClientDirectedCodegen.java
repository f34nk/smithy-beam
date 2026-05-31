package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamAwsJson10ProtocolCodegen;
import io.smithy.beam.core.BeamAwsJson11ProtocolCodegen;
import io.smithy.beam.core.BeamRestJson1ProtocolCodegen;
import io.smithy.beam.core.BeamProtocolResolver;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        String serviceName = directive.service().getId().getName();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns, serviceName);
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
        ServiceShape service = directive.service();
        BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
        BeamProtocolCodegen protocolCodegen = null;
        Optional<ShapeId> serviceProtocol =
                BeamProtocolResolver.resolveServiceProtocol(directive.model(), service);
        ShapeId resolvedProtocolTraitId = serviceProtocol.orElse(null);
        if (serviceProtocol.isPresent()) {
            protocolCodegen =
                    BeamProtocolCodegenFactory.create(directive.model(), serviceProtocol.get());
        }
        String ns = service.getId().getNamespace();
        String serviceName = service.getId().getName();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns, serviceName);
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
                service,
                httpBindings,
                protocolCodegen,
                resolvedProtocolTraitId,
                moduleName,
                definitionFile);
    }

    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        ServiceShape service = ctx.service();
        BeamProtocolResolver.resolveServiceProtocol(directive.model(), service)
                .ifPresent(
                        protocol ->
                                BeamProtocolResolver.assertClosureSupported(
                                        directive.model(), service, protocol));

        String ns = service.getId().getNamespace();
        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), ns, service.getId().getName());

        ctx.writerDelegator().useFileWriter(
                layout.runtimeTypesHeaderFile(),
                writer -> {
                    writer.write("%% Generated runtime types for $L.", ctx.service().getId());
                    ErlangRuntimeTypesEmitter.writeBody(writer);
                });

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
        ErlangContext ctx = directive.context();
        ServiceShape service = directive.shape();

        if (ctx.protocolCodegen() != null
                && BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangRestJson1Emitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsJson10Emitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsJson11Emitter.emitCodecModule(ctx, directive.shape());
        }

        ErlangHttpDispatchEmitter.emit(ctx, service);
        ErlangPaginatorEmitter.emit(ctx, service);
        BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
        for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
            ErlangResourceEmitter.emitClient(ctx, resource);
        }

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write("%% Service closure: $L", service.getId());
            writer.write(
                    "%% Client configuration is intentionally opaque at this layer; "
                            + "endpoint, transport, and protocol live in future runtime modules.");
            writer.write("-type client_config() :: #{binary() => term()}.");
            writer.write("");
            BeamAwsServiceMetadata.from(service).ifPresent(meta -> {
                writer.write("%% AWS service metadata from model:");
                writer.write("%%   sdkId: $L", meta.sdkId());
                writer.write("%%   endpointPrefix: $L", meta.endpointPrefix());
                writer.write("default_config() ->");
                writer.write("    #{region => <<\"us-east-1\">>,");
                writer.write("      endpoint_prefix => <<\"$L\">>,", meta.endpointPrefix());
                writer.write("      signing_name => <<\"$L\">>>}.", meta.signingName());
                writer.write("");
                writer.write("resolve_base_url(#{endpoint_prefix := Prefix, region := Region}) ->");
                writer.write("    <<\"https://\", Prefix/binary, \".\", Region/binary, \".amazonaws.com\">>.");
                writer.write("");
            });
            writer.popState();
        });
    }

    @Override
    public void generateOperation(
            GenerateOperationDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        OperationShape op = directive.shape();
        SymbolProvider sp = directive.symbolProvider();
        Symbol opSym = sp.toSymbol(op);

        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), ctx.service().getId().getNamespace(), ctx.service().getId().getName());
        boolean hasProtocol = ctx.protocolCodegen() != null
                && (BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(ctx.protocolCodegen().protocolTraitId())
                        || BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(
                                ctx.protocolCodegen().protocolTraitId())
                        || BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(
                                ctx.protocolCodegen().protocolTraitId()));

        BeamDocumentation.forShape(op).ifPresent(doc -> {
            ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
                writer.pushOperationBodySection();
                BeamDocumentation.writeErlangDoc(writer, doc);
                writer.popState();
            });
        });

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write(
                    "-spec $L(client_config(), $L) -> {'ok', $L} | {'error', term()}.",
                    opSym.getName(), inSym.getName(), outSym.getName());
            if (hasProtocol) {
                String codecModule = layout.clientCodecModuleName(ctx.resolvedProtocolTraitId());
                writer.write("$L(Config, Input) ->", opSym.getName());
                writer.indent();
                if (ErlangRestJson1Emitter.serviceHasHostLabelOperations(ctx.model(), ctx.service())) {
                    writer.write("Req = $L:encode_$L_request(Config, Input),",
                            codecModule, opSym.getName());
                } else {
                    writer.write("Req = $L:encode_$L_request(Input),",
                            codecModule, opSym.getName());
                }
                writer.write("case $L:dispatch(Config, Req) of",
                        layout.runtimeHttpModuleName());
                writer.indent();
                writer.write("{ok, Resp} ->");
                writer.indent();
                writer.write("$L:decode_$L_response(Resp);",
                        codecModule, opSym.getName());
                writer.dedent();
                writer.write("{error, Reason} ->");
                writer.indent();
                writer.write("{error, Reason}");
                writer.dedent();
                writer.dedent();
                writer.write("end.");
                writer.dedent();
            } else {
                writer.write("$L(_Config, _Input) -> {error, not_implemented}.", opSym.getName());
            }
            writer.write("");
            writer.popState();
        });
    }

    @Override
    public void generateResource(
            GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
        // Emitted from generateService for all contained resources.
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
