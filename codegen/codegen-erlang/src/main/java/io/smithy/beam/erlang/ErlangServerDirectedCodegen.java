package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-specific DirectedCodegen pass. Types are emitted by {@link ErlangTypeGeneration}
 * before this runs; this class must not write type files again.
 */
final class ErlangServerDirectedCodegen
        implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout =
                new BeamErlangLayout(settings, ns, directive.service());
        String definitionFile = layout.serverModuleFile();
        return SymbolProvider.cache(
                new ErlangSymbolProvider(
                        settings,
                        directive.model(),
                        directive.service(),
                        definitionFile,
                        BeamCodegenKind.SERVER));
    }

    @Override
    public ErlangContext createContext(
            CreateContextDirective<BeamSettings, ErlangIntegration> directive) {
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
        BeamErlangLayout layout =
                new BeamErlangLayout(settings, ns, service);
        String definitionFile = layout.serverModuleFile();
        String moduleName = layout.serverModuleName();
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
        BeamEdition edition = BeamEdition.fromSettings(directive.settings());
        BeamProtocolResolver.resolve(directive.model(), service, directive.settings())
                .ifPresent(
                        protocol ->
                                BeamProtocolResolver.assertClosureSupported(
                                        directive.model(), service, protocol, edition));

        String ns = service.getId().getNamespace();
        BeamErlangLayout layout =
                new BeamErlangLayout(ctx.settings(), ns, service);

        ctx.writerDelegator().useFileWriter(
                layout.runtimeTypesHeaderFile(),
                writer -> {
                    writer.write("%% Generated runtime types for $L.", ctx.service().getId());
                    ErlangRuntimeTypesEmitter.writeBody(writer, Optional.empty());
                });

        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
        ErlangBehaviourEmitter.beginService(ctx, service, operations);

        String behaviourMod = layout.behaviourModuleName();

        List<String> exports = new ArrayList<>();
        exports.add("init_handlers/0");
        for (OperationShape op : operations) {
            Symbol sym = directive.symbolProvider().toSymbol(op);
            exports.add("handle_" + sym.getName() + "/3");
        }
        String exportList = String.join(", ", exports);

        ctx.writerDelegator().useFileWriter(layout.serverModuleFile(), writer -> {
            writer.pushGeneratedDocumentationSection();
            writer.write("%% Generated Erlang server dispatcher for $L.", service.getId());
            writer.write("%% Discovers impl callbacks at startup via init_handlers/0.");
            writer.popState();

            writer.pushModuleHeaderSection();
            writer.write("-module($L).", layout.serverModuleName());
            writer.write("-behaviour($L).", behaviourMod);
            writer.write("-export([$L]).", exportList);
            writer.popState();

            writer.pushDependenciesSection();
            ((ErlangImports) writer.getImportContainer()).addIncludeRelative(layout.typesHeaderFile());
            writer.write(ErlangImports.relativeIncludeLine(layout.typesHeaderFile()));
            writer.popState();

            writer.pushModuleHeaderSection();
            writer.write("");
            writer.popState();

            writer.pushProtocolHookSection();
            writer.write("%% Handler discovery and dispatch helpers.");
            writer.popState();
        });
    }

    @Override
    public void customizeBeforeIntegrations(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        // Reserved for server integrations.
    }

    @Override
    public void customizeAfterIntegrations(
            CustomizeDirective<ErlangContext, BeamSettings> directive) {
        // Reserved for server integrations.
    }

    @Override
    public void generateService(
            GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        ServiceShape service = directive.shape();

        if (ctx.protocolCodegen() != null
                && BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangRestJson1Emitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsJson10Emitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsJson11Emitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamRestXmlProtocolCodegen.REST_XML.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangRestXmlEmitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamAwsQueryProtocolCodegen.AWS_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsQueryEmitter.emitServerCodecModule(ctx, service);
        } else if (ctx.protocolCodegen() != null
                && BeamEc2QueryProtocolCodegen.EC2_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangEc2QueryEmitter.emitServerCodecModule(ctx, service);
        }

        ErlangRouterEmitter.emit(ctx, service);
        ErlangComplianceTestEmitter.emit(ctx, service);
        BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
        for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
            ErlangResourceEmitter.emitServer(ctx, resource);
        }

        BeamErlangLayout layout =
                new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
        ErlangBehaviourEmitter.finishService(ctx, operations, directive.symbolProvider());
        ErlangHandlerDiscoveryEmitter.emitDiscoveryHelpers(ctx, layout);

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write(
                    "%% Call $L:init_handlers/0 during application start before dispatch.",
                    ctx.moduleName());
            writer.write(
                    "%% Default impl module: $L.",
                    layout.implModuleName());
            writer.write("");
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
        String handler = "handle_" + opSym.getName();

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            ErlangHandlerDiscoveryEmitter.emitOperationDispatch(writer, handler);
            writer.popState();
        });

        ErlangBehaviourEmitter.emitOperationCallback(ctx, op, sp);
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
