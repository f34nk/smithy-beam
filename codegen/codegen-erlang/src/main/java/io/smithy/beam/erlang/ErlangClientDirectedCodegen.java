package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamRestJson1ProtocolCodegen;
import io.smithy.beam.core.BeamProtocolResolver;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                service,
                httpBindings,
                protocolCodegen,
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
        }

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write("%% Service closure: $L", service.getId());
            String override = ctx.settings().baseUrl();
            if (override != null && !override.isBlank()) {
                writer.write("%% Default base URL from smithy-build plugin setting baseUrl.");
                writer.write(
                        "-define(BEAM_DEFAULT_BASE_URL, <<\"$L\">>).",
                        ErlangStringLiterals.escapeBinaryContents(override));
            }
            writer.write(
                    "%% Client configuration is intentionally opaque at this layer; "
                            + "endpoint, transport, and protocol live in future runtime modules.");
            writer.write("-type client_config() :: #{binary() => term()}.");
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

        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);

        String clientFile = ctx.definitionFile();

        ctx.writerDelegator().useFileWriter(clientFile, writer -> {
            writer.pushOperationBodySection();
            writer.write(
                    "-spec $L(client_config(), $L) -> {'ok', $L} | {'error', term()}.",
                    opSym.getName(),
                    inSym.getName(),
                    outSym.getName());
            writer.write("$L(_Cfg, _Input) -> {error, not_implemented}.", opSym.getName());
            writer.write("");
            writer.popState();
        });

        if (ctx.protocolCodegen() != null) {
            ctx.writerDelegator().useFileWriter(clientFile, writer -> {
                ctx.protocolCodegen().emitOperationBindings(ctx, ctx.service(), op);
                writer.pushOperationBodySection();
                writer.write("%% HTTP request bindings for $L:", op.getId());
                for (Map.Entry<String, HttpBinding> entry :
                        ctx.httpBindings().requestBindings(op).entrySet()) {
                    HttpBinding binding = entry.getValue();
                    writer.write("%%   $L @ $L", entry.getKey(), binding.getLocation());
                }
                writer.write("");
                writer.popState();
                for (ErlangIntegration integration : ctx.integrations()) {
                    integration.customizeProtocolSerialize(ctx, op, writer);
                }
            });
        }
    }

    @Override
    public void generateResource(
            GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        ResourceShape resource = directive.shape();

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.write("%% Contained resource: $L", resource.getId());
        });
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
